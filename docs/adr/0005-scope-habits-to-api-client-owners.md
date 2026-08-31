# ADR 0005: Scope habits to authenticated API client owners

- Status: Proposed
- Date: 2026-08-29
- Supersedes: ADR 0004 when implemented
- Implementation: [notes and step order](../implementation/0005-habit-ownership.md); V17 applied, application changes not started

## Context

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

When this ADR is implemented, ADR 0004 is superseded. The `api-client-tiers` namespace is retired from the authentication path. A future authentication cache must use a new, versioned cache name and serializer, so existing `ClientTier` entries are never deserialized as client contexts.

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

ADR 0004 remains the accepted description of the current implementation until this proposal is implemented. At implementation time its stale-tier acceptance no longer applies, because identity becomes an authorisation input rather than response-shaping metadata.
