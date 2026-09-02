# ADR 0005: Scope habits to authenticated API client owners

- Status: Accepted
- Date: 2026-08-29
- Supersedes: [ADR 0004](0004-accept-stale-client-tier-bounded-by-ttl.md), as of 2026-09-01
- Implementation: [notes and step order](../implementation/0005-habit-ownership.md); steps 1–4 implemented (V17, authenticated test suite, authentication boundary, owner scoping in SQL), V18 and backfill deferred

## Context

> This section describes the code as measured on 2026-08-29, before implementation. What has since
> changed is recorded under [Implementation status](#implementation-status-2026-09-01) and
> [Owner scoping as built](#owner-scoping-as-built-2026-09-02).

All habits currently belong to one global data set. `HabitMapper.xml` has eight statements over `habits`: `findById`, `existsById`, `deleteById`, `findActive`, `insert`, `update`, `search` and `count`. The shared `searchWhere` fragment affects both `search` and `count`. None of these statements carries an owner predicate.

API clients already have a database identity. `ApiClient.id` is the primary key, while `api_key_hash` is its credential and `tier` controls response-field visibility. `ClientTierResolver.resolve` currently returns only `ClientTier`; a missing API key returns anonymous `PUBLIC` without a database lookup.

Authentication is opt-in at controller-parameter level. `ClientTierArgumentResolver.supportsParameter` runs only for parameters annotated with `@ResolvedClientTier`. Nine of the sixteen `/habits` endpoints declare that parameter. Seven do not: bulk complete, delete, stats, history, completion rate, due-today count and dashboard stats. Evolving only this argument resolver therefore cannot enforce authentication for the complete controller.

Adding a non-null owner in one migration is unsafe. This was measured on MySQL with `STRICT_TRANS_TABLES` and 229 existing habit rows. `ADD owner_id BIGINT NOT NULL` succeeded silently and assigned `0` to all rows. Adding the foreign key to `api_clients.id` then failed with error `1452`. The dangerous operation succeeds before referential integrity stops the migration.

The current local database has one provisioned client: `Local dev`, tier `INTERNAL`, currently ID `1`, identified by the unique hash `60a2286a5007c8e4c2664246e14f73936f55b0b96b4652933d90e21b2fa068b8`.

The existing `api-client-tiers` Redis cache stores values through a serializer typed as `ClientTier` and keeps them for one minute. ADR 0004 accepts that staleness only while tier affects response fields rather than data ownership.

Dashboard caching is global. The normal key is `generation::today`; the Redis-failure path creates `bypass::UUID::today`; and the `@Cacheable` condition disables caching when `spring.cache.type` is not `redis`. `DashboardCacheKeyGenerator` obtains `today` through a positional cast from `params[0]`.

Completion history and completion statistics have no independent owner identity. They are linked to a habit through `habit_id`.

## Decision

Use `api_clients.id` as the owner identity. The API key hash remains a credential of that identity and is not copied into habit rows.

### Authentication boundary

Add a `HandlerInterceptor` for `/habits` and `/habits/**`. It resolves the API key before every habit controller method, rejects missing, unknown or inactive credentials with `401 Unauthorized`, and stores an immutable client context containing `clientId` and `tier` as a request attribute.

The argument resolver becomes a consumer of that request attribute rather than the component that decides whether authentication runs. Controller methods that require the context for owner propagation declare it explicitly, but omission of a parameter cannot bypass the interceptor.

A provisioned client with tier `PUBLIC` has an identity and can own habits. An anonymous request has no identity and cannot access owner-scoped `/habits` endpoints.

Identity resolution is not served from the existing one-minute tier cache. It performs a database lookup on every request until an authentication cache with explicit write invalidation is designed. This adds database cost but prevents a revoked credential from retaining access to an owner's data for the tier TTL.

ADR 0004 is superseded as of 2026-09-01. The `api-client-tiers` namespace is retired from the authentication path. A future authentication cache must use a new, versioned cache name and serializer, so existing `ClientTier` entries are never deserialized as client contexts.


#### Client context propagation addendum — 2026-09-02

The step-4 entry condition was measured before writing owner-scoped SQL. A
second API client was provisioned with exactly one habit whose `owner_id`
references that client. Running the existing suite as the first client produced
18 failures: 14 in `HabitControllerIntegrationTest` and four in
`HabitStatsIntegrationTest`.

That number is an accidental-detection baseline, not a coverage count. The 18
tests expose aggregate leakage through list/count, due-today and dashboard
queries. They do not exercise cross-owner access by ID. Ten of the sixteen
controller endpoints accept a habit ID, and the pre-ownership suite always
targets a habit created by the same test. Those ten paths therefore require new
cross-owner tests that prove foreign and nonexistent IDs are both reported as
`404 Not Found`. Closing the aggregate baseline from 18 failures to zero is
necessary but is not sufficient evidence that owner scoping is complete.

The authenticated client ID reaches the service layer through an explicit
`ClientContext` controller parameter resolved from the request attribute set by
`ClientAuthenticationInterceptor`. `ClientContext` replaces the existing
`ClientTier` parameter in all sixteen habit-controller signatures. Endpoints
that need response filtering read `context.tier()`, and every endpoint passes
`context.clientId()` explicitly into its service and repository operations.

Argument resolution remains only a consumer of the request attribute. Omitting
the controller parameter cannot bypass authentication because the interceptor
still runs for `/habits` and `/habits/**` before argument resolution.

Services do not read `RequestContextHolder` and remain independent of HTTP
infrastructure. Reading client context from `RequestContextHolder` was rejected
because it would hide the ownership input and couple business services to
request state.

`HabitCompletedEventConsumer` does not receive a separate owner ID. Ownership
is inherited through `habitId`: the producer publishes only after the
authenticated command path has selected an owned habit, and `habits` remains
the single ownership source of truth for completion history and statistics.
Adding `ownerId` to the Kafka event payload was rejected for this step because
it would be a breaking topic-format change, would not describe already queued
messages, and would duplicate ownership already represented by the habit
foreign-key relationship.

### Schema and migration sequence

Use an expand/backfill/contract sequence:

1. V17 adds `api_clients.active BOOLEAN NOT NULL DEFAULT TRUE`.
2. V17 adds nullable `habits.owner_id`, an index and a foreign key to `api_clients.id` with `ON DELETE RESTRICT`.
3. The application scopes all habit operations by the authenticated client ID. Rows with `owner_id IS NULL` are inaccessible through the API.
4. Each environment records an explicit legacy-owner mapping before backfill. The owner is selected by unique API-key hash, not by an assumed numeric ID.
5. In the measured local environment, all 229 legacy rows map to the `Local dev` client identified by hash `60a2286a5007c8e4c2664246e14f73936f55b0b96b4652933d90e21b2fa068b8`.
6. An environment with legacy rows belonging to multiple clients must provide an explicit per-row mapping; it cannot use the single-owner backfill.
7. V18 may make `owner_id` non-null only after a verification query proves that no null values remain.

Revocation sets `active=false`; key rotation changes `api_key_hash` while preserving `api_clients.id`. Deleting an owner with habits is rejected by the foreign key and does not cascade-delete user data.

Integration-test cleanup must delete owned habits before deleting their API client. Existing teardown code that deletes an `ApiClient` directly will otherwise fail with foreign-key error `1451`.

### Owner-scoped behavior

Every read, insert, update and delete includes the owner ID in its database operation. A preceding ownership check alone is insufficient.

A valid client requesting another owner's single habit receives `404 Not Found`, not `403`. Nonexistent and foreign IDs are deliberately indistinguishable.

Bulk completion keeps partial-result semantics. IDs owned by the caller are processed; nonexistent and foreign IDs both appear in `notFound`. The response must not reveal which case occurred.

Completion history, completion rate and per-habit statistics enforce ownership through a join to `habits`. Dashboard statistics do not require that join after `findActive` becomes owner-scoped, because their completion-stat query receives only IDs selected for that owner.

`habit_completions` and `habit_completion_stats` do not receive duplicate `owner_id` columns. `habits` remains the single ownership source of truth.

### Dashboard cache

The reusable dashboard key becomes:

`ownerId::generation::today`

Owner ID is first so a future per-owner eviction can target `dashboard-stats::<ownerId>::*`.

Do not insert `ownerId` blindly as a new first method argument while leaving the current positional cast in `DashboardCacheKeyGenerator`; that would turn `(LocalDate) params[0]` into a runtime `ClassCastException`.

Replace the positional contract with key construction that receives named or typed `ownerId` and `today` values. Tests must cover argument reordering and both generated forms:

- Normal Redis path: `ownerId::generation::today`.
- Generation-read failure: `bypass::UUID::ownerId::today`.

The bypass UUID prevents cache reuse but does not authorise the query; the underlying dashboard read must still be owner-scoped. When the `@Cacheable` condition is false, no reusable cache entry exists and isolation comes entirely from database predicates.

The generation may initially remain global. One client's mutation can invalidate every owner's dashboard entry, which is inefficient but does not mix data.

## Alternatives considered

Keeping authentication in the argument resolver was rejected because it is opt-in. Seven existing endpoints demonstrate that a controller method can omit the parameter and bypass resolution.

Caching the combined client identity and tier in the existing `api-client-tiers` cache was rejected. A stale entry would retain access to owner-scoped data, violating ADR 0004's accepted information-disclosure boundary. Its serializer is also typed as `ClientTier`, so changing the value type in place is a breaking Redis format change.

A free-form owner string was rejected because it creates a second identity namespace, lacks referential integrity and requires another trusted mapping from credential to owner.

A synthetic legacy owner was rejected because it invents ownership inside a migration. The measured MySQL behavior already demonstrated the accidental form of this design as owner `0`.

A shared anonymous `PUBLIC` owner was rejected because all unauthenticated callers would become one tenant.

Copying `owner_id` into completion and statistics tables was rejected because ownership could drift across three tables. Joins cost more, but preserve one source of truth.

Returning `403` for another owner's ID was rejected because it confirms that the ID exists. Returning `404` preserves tenant isolation.

## Consequences

This is a breaking API change: missing API keys that currently receive filtered `PUBLIC` responses receive `401` on `/habits`.

Authentication gains one database lookup per request. Correct revocation and tenant isolation are preferred over reusing the current one-minute cache.

The implementation touches all sixteen controller endpoints, the interceptor and argument resolver, all eight MyBatis statements and the shared `searchWhere` fragment. Tests require at least two clients and must prove positive access, cross-owner `404`, mixed-owner bulk behavior and anonymous `401` on endpoints that currently have no tier parameter.

The schema change requires at least two migrations and an explicit operational backfill. Legacy rows are temporarily unavailable through the API until assigned; they are never exposed globally during transition.

Per-habit completion queries gain ownership joins. Dashboard completion-stat queries continue using the already owner-scoped habit ID set.

The dashboard cache keeps global invalidation initially. This may discard valid entries for unrelated owners but cannot return one owner's dashboard to another.

ADR 0004's stale-tier acceptance no longer applies as of 2026-09-01, because identity became an authorisation input rather than response-shaping metadata, and the cache it accepted staleness on no longer exists.

## Implementation status (2026-09-01)

Steps 1–3 are implemented. Step 4, owner scoping in the eight `HabitMapper.xml` statements and the shared `searchWhere` fragment, was not started at this date; it was completed on 2026-09-02 and is recorded separately below.

### What was removed

`ClientTierResolver` and `ClientTierLookup` are deleted, together with `ClientTierResolverTest`, `ClientTierCacheTest` and `ClientTierCacheIT`. `RedisCacheConfig` no longer declares the `api-client-tiers` cache name, its one-minute TTL, its `ClientTier`-typed serializer or its cache configuration. The dead path was deleted rather than left in place unused, so no annotation survives that no code reaches.

Redis keys written under `api-client-tiers` before this change are not deleted by the application. They remain inert: nothing reads that namespace any more, and they expire on their own one-minute TTL. This is why a future authentication cache must take a new, versioned name — a stale `ClientTier` value must never be reachable as a `ClientContext`.

### Authentication boundary as built

`ClientAuthenticationInterceptor` is registered for `/habits` and `/habits/**`. It rejects a missing, unknown or inactive API key with `401`, and on success stores an immutable `ClientContext(clientId, tier)` as a request attribute. `ClientIdentityLookup` performs the database lookup and carries no `@Cacheable`. `ClientTierArgumentResolver` now only reads that attribute; it holds no dependency capable of resolving a key itself.

### Verification

The measured step-3 entry condition from the implementation notes is closed. With a fixture that returns an API key without persisting the client, **all 78 controller tests fail with `401`** — where before the interceptor 49 failed and 29 passed. The 29 included **15 that previously returned `400`**, which is the specific evidence that authentication now precedes `@Valid` body validation rather than running after it. All seven endpoints that declare no `@ResolvedClientTier` parameter are covered by explicit tests.

Two further mutations are RED: removing the `active` guard from `ClientIdentityLookup` lets a revoked client receive `200` instead of `401`; adding `@Cacheable` to the identity lookup drops repository calls from two to one across two requests and fails the uncached-lookup test.

Full suite after the removals: 227 tests, 0 failures, 0 errors, 0 skipped.

### Metric change

`habit.client.tier.resolutions` keeps its name for compatibility, but now publishes only `outcome="resolved"` and `outcome="rejected"`; its description reads "Number of API key authentication outcomes". The `outcome="public"` counter is removed rather than left registered at a permanent zero, since anonymous access is no longer a possible outcome on `/habits`.

## Owner scoping as built (2026-09-02)

Step 4 is implemented. Habits are scoped to the authenticated API client. V18 `NOT NULL`, the per-environment backfill and an authentication cache remain deferred, so rows with `owner_id IS NULL` still exist and are unreachable through the API as specified.

### Propagation and statements

`ClientContext` replaced `ClientTier` in all sixteen controller signatures; `context.tier()` is used only for response filtering and `context.clientId()` is passed explicitly through service to mapper. No service reads `RequestContextHolder`.

All eight `HabitMapper.xml` statements carry `owner_id`, `habitResultMap` maps the column, and `insert` writes it. `searchWhere` opens with an unconditional `owner_id = #{ownerId}` so no `<if>` can omit the predicate. `update` carries ownership in the same `WHERE` as the optimistic-lock version check, so the conflict-retry path cannot widen access.

### Verification

Entry condition, measured before any change: a second client with one owned habit made 18 existing tests fail — 14 controller, four dashboard/stat. That number is an accidental-detection baseline, not a coverage count; it detects aggregate leakage only. Seven of `HabitStatsIntegrationTest`'s 11 tests could not have failed regardless, because they address a habit the test itself created.

Exit condition: the same mutation now leaves 241 of 241 passing. Access by ID is covered separately by a `@ParameterizedTest` over an enum with one case per `{id}` endpoint — all ten — asserting `404` for a foreign habit, plus a mixed-owner bulk test that asserts the foreign ID appears in `notFound` and that its completion count is unchanged afterwards.

Both entry and exit numbers above are **surefire only** (`mvn test`, 40 classes, 241 tests). They are comparable to each other but they are not a verification of the cut: the four `*IT` classes bind to failsafe and were not executed while step 4 was being written. The first `mvn verify` after the step failed, on `DashboardCacheRaceIT` — see [Bug 36](#bug-36-the-first-verify-after-this-step-failed-2026-09-02) below.

Measured after that fix: surefire 241 / 0 / 0 / 0 and failsafe 13 / 0 / 0 / 0, `./mvnw -B clean verify` exit 0.

### Bug 36: the first `verify` after this step failed (2026-09-02)

`DashboardCacheRaceIT` built its habit with the two-argument `Habit` constructor, so the row carried `owner_id = NULL` while the test read the dashboard as owner `501`. Owner-scoped `findActive(ownerId)` no longer saw the row, `activeHabitIds` came back empty, `HabitQueryService` short-circuits `findLatestByHabitIds` on an empty list, the test's `@Around` aspect on that method therefore never fired, and the latch it counts down timed out. Deterministic, not a race: the failure message named the timeout, the defect was the ownerless fixture.

Fixed by provisioning the owner through `TestApiClientOwner.ensureExists` and using the three-argument constructor; the two cache keys the test builds also needed `ownerId` prepended to match `DashboardCacheKeyGenerator`.

The finding worth keeping is not the fixture. It is that the exit condition above was measured with a command that does not run integration tests, so a green suite and a passing build were different claims for the whole of step 4.

### Deviations from the decision, accepted knowingly

Two places do not match this ADR as written. Both are recorded rather than silently reconciled.

"Every read, insert, update and delete includes the owner ID in its database operation. A preceding ownership check alone is insufficient", and "completion history, completion rate and per-habit statistics enforce ownership through a join to `habits`". No such join was written. Five paths — `getHistory`, `getCompletionRate`, `getStatsProjection`, `complete` and `uncomplete` — perform an owner-scoped habit lookup and then query `HabitCompletionRepository` / `HabitCompletionStatRepository` by `habit_id` alone; neither repository was changed by step 4. Dashboard stats is the one completion access that does match, because it passes an ID set already selected by `findActive(ownerId)`.

What holds the five is `fk_habit_completions_habit` plus the now owner-scoped `habits` table, in one transaction: a `habit_id` obtained through an owner-scoped read cannot belong to another owner. Asserting ownership directly would require either an owner column on those tables, which this ADR rejects, or the join above. The protection is inherited, not asserted, which is worth naming because the line that looks load-bearing — the preceding check — is not the one that holds, and nothing enforces that a future completion access will be preceded by an owner-scoped lookup at all.

"The dead path was deleted rather than left in place unused, so no annotation survives that no code reaches." `ClientTierArgumentResolver.supportsParameter` still accepts `ClientTier` alongside `ClientContext`, and after step 4 that branch is reachable only from its own unit test. It is retained as a resolver-level compatibility affordance; to honour the step-3 precedent it should be removed together with that test.

### Deferred cleanups this step created

`Habit(String, Instant)` delegates to `this(null, name, createdAt)` and is unused in `src/main/java`. It builds a habit that `insert` writes with `owner_id = NULL`.

This was first recorded here as a V18 problem, on the grounds that no production code calls it. That boundary was wrong and Bug 36 above disproved it the same day: 39 test call sites use it, and owner scoping alone — without `NOT NULL` — was enough to break one integration test. The constructor is a live hazard for any test that reads through an owner-scoped statement, not a future migration hazard. One further call site is latent today: `HabitCompletionRepositoryIntegrationTest` saves an ownerless habit and queries completions by `habit_id`, never through `findActive`, so it passes. Whether to delete the constructor, or keep it with a test that documents it as unit-only, is an open decision and not deferrable to V18.

`TestApiClientOwner.ensureExists` uses H2's `MERGE INTO ... KEY (id)`. All seven current callers are H2 tests; the two `MySqlIT` classes provision owners themselves. The name carries no dialect hint, so the first `MySqlIT` to reuse it fails for a reason its call site does not suggest.

It is kept as a deliberate test-only helper rather than renamed in this step: no production path is affected, and V18 requires every owner fixture to be revisited regardless, since `owner_id NOT NULL` removes the option of an unowned habit row. Both fixture items above are consequently preconditions of V18, not independent cleanups.
