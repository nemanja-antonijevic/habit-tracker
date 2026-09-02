# Implementation notes: ADR 0005 habit ownership

Companion to [ADR 0005](../adr/0005-scope-habits-to-api-client-owners.md). The ADR records the
decision; this file records the ordering constraints and the traps found while reviewing it, so they
are not rediscovered during implementation.

Status: steps 1–4 complete (V17 applied, test suite authenticated, authentication boundary in place,
owner scoping in SQL). Habits are scoped to the authenticated API client. V18 `NOT NULL`, the
per-environment backfill and an authentication cache remain deferred.

## Step order

The order below is not a preference. Each step fails in a way that hides the next one if taken early.

### 1. V17 — schema expand (done)

`V17__add_habit_owner_and_client_revocation.sql` adds `api_clients.active`, nullable
`habits.owner_id`, `idx_habits_owner`, and `fk_habits_owner` with `ON DELETE RESTRICT`.

Verified on H2 2.3.232 in `MODE=MySQL` before writing the file: all four statements succeed, existing
`api_clients` rows receive `active = TRUE`, existing `habits` rows receive `owner_id = NULL`, an
insert with an unknown `owner_id` is rejected, an insert with `owner_id = NULL` is accepted, and
deleting a client that owns a habit is rejected.

`owner_id` is nullable on purpose. On MySQL with `STRICT_TRANS_TABLES`,
`ADD owner_id BIGINT NOT NULL` **succeeds** and assigns `0` to every existing row; only the
subsequent foreign key fails, with error `1452`. The unsafe operation completes before referential
integrity stops the migration, so the non-null form must not be used until backfill is verified.

### 2. Migrate the existing test suite to an authenticated client (done)

**This must precede any ownership test.** Seven test classes exercise `/habits`; five of them did not
reach the controller as an authenticated client, but not all for the same reason:

| Test class | Before step 2 | Survives the interceptor |
| --- | --- | --- |
| `HabitControllerIntegrationTest` | `@MockBean ClientTierResolver` stubbed to `INTERNAL` | no |
| `HabitStatsIntegrationTest` | no API key, anonymous `PUBLIC` | no |
| `HabitStreakConsistencyIntegrationTest` | no API key, anonymous `PUBLIC` | no |
| `HabitCompletionConcurrencyIntegrationTest` | no API key, anonymous `PUBLIC` | no |
| `HabitCompletionConcurrencyMySqlIT` | no API key, anonymous `PUBLIC` | no |
| `ClientTierFilteringIntegrationTest` | real provisioned client | yes |
| `PrometheusConfigurationIntegrationTest` | real provisioned client | yes |

The mocked resolver is the trap worth recording. It made 28 tier-gated assertions in
`HabitControllerIntegrationTest` pass without any client row existing, and it would **not** have saved
that class from `401`, because the interceptor performs its own lookup rather than calling the
resolver. The mechanism that appeared to protect the class does not reach the place where the decision
is made.

All five now provision a real `INTERNAL` client through `InternalApiClientFixture` and send the header
via a per-class `perform(...)` helper. The header is deliberately not a MockMvc default, so tests that
assert behaviour for an absent key still assert it.

The header name is `X-Api-Key`, from `ClientTierArgumentResolver.API_KEY_HEADER`.

Two measured baselines from this step are the reference points for step 3:

- Forcing the fixture to `PUBLIC` breaks 20 of 78 controller tests, all on the filtered-away
  `scheduledDays` and `archived` fields. Tier filtering is genuinely exercised.
- A fixture that never persists the client breaks 49 with `401`, while **29 still pass** on a
  nonexistent key — request-validation tests that never reach a tier decision. Step 3 must flip those
  29 to `401`; if it does not, the interceptor is not covering the whole controller.

### 3. Interceptor before controller changes (done)

`ClientTierArgumentResolver.supportsParameter` required the `@ResolvedClientTier` annotation, so
resolution is opt-in per parameter. `HabitController` has 16 endpoints and only 9 declare that
parameter; the other 7 (bulk complete, delete, stats, history, completion rate, due-today count,
dashboard stats) were unreachable by the resolver. Authentication that lives there cannot cover 44% of
the controller, which is why ADR 0005 moved the boundary to a `HandlerInterceptor` over `/habits` and
`/habits/**`.

`ClientAuthenticationInterceptor` now owns the decision and stores an immutable
`ClientContext(clientId, tier)` as a request attribute; `ClientTierArgumentResolver` only reads it and
no longer holds a dependency that could resolve a key. Identity comes from `ClientIdentityLookup`,
which carries no `@Cacheable` — the old `ClientTierResolver`, `ClientTierLookup` and the
`api-client-tiers` cache were deleted with this step.

The step-2 baseline closed as required: with an unpersisted fixture all **78 of 78** controller tests
return `401`, against 49 fail / 29 pass before. **15 of those 29 previously returned `400`**, which is
what proves authentication now runs ahead of `@Valid`; the rest were endpoints with no tier parameter,
and all seven are covered explicitly. Removing the `active` guard (revoked client gets `200`) and
adding `@Cacheable` to the identity lookup (two requests, one repository call) are both RED. Suite
after the deletions: 227 / 0 / 0 / 0.

### 4. Owner scoping in SQL, not above it (done)

`HabitMapper.xml` has eight statements over `habits` — `findById`, `existsById`, `deleteById`,
`findActive`, `insert`, `update`, `search`, `count` — plus the shared `searchWhere` fragment used by
`search` and `count`. Every one needs the owner in the statement itself. A check performed before the
call is not equivalent: it leaves a window and it does not protect callers that bypass the check.

All eight now carry `owner_id`, and `searchWhere` opens with an unconditional `owner_id = #{ownerId}`
so the predicate cannot be skipped by a `<if>` that happens to be false. `insert` writes
`#{ownerId,jdbcType=BIGINT}` and `habitResultMap` maps the column, so the owner round-trips rather
than being filtered on a column nothing populates.

`update` is the one worth reading twice: its `WHERE` is `owner_id = … AND id = … AND version = …`.
Ownership sits in the same predicate as the optimistic lock, so a cross-owner update cannot slip
through the conflict-retry path either — the retry re-reads through the owner-scoped `findById`.

`ClientContext` replaced `ClientTier` in all sixteen controller signatures; `context.clientId()` is
passed explicitly down to the mapper and `context.tier()` is used only for response filtering. No
`@ResolvedClientTier ClientTier` parameter survives in `src/main/java`.

#### Entry and exit measurement

Entry condition, measured before any change: provisioning a second client with one owned habit and
running the existing suite as the first client produced **18 failures** — 14 in
`HabitControllerIntegrationTest`, four in `HabitStatsIntegrationTest`.

That number is an accidental-detection baseline, not a coverage count, and the composition proves it.
`HabitStatsIntegrationTest` has 11 tests; exactly the **four** `getDashboardStats_*` ones failed. The
other seven — `getStats_*`, `uncomplete_*`, the streak pair — are `{id}` paths against a habit the
test itself created, so they could not fail no matter how leaky the scoping was. The baseline sees
aggregate leakage only.

Exit condition: the same mutation (second client, sentinel habit) now leaves **241 of 241** passing.
Cross-owner access by ID is covered separately by `crossOwnerIdEndpointReturns404`, a
`@ParameterizedTest` over a `CrossOwnerEndpoint` enum with one case per `{id}` endpoint — ten in one
table rather than ten near-copies — plus `bulkCompleteTreatsForeignHabitAsNotFound`, which asserts the
foreign habit appears in `notFound` **and** re-reads it afterwards to prove its completion count is
unchanged.

**Both numbers are surefire only.** `18 → 0` and `241 / 0 / 0 / 0` come from `mvn test`, which runs
40 classes and 241 tests and binds nothing named `*IT`. The four integration-test classes go through
failsafe on `verify`, and they were not executed once while step 4 was being written — not locally,
not through the assistant. Entry and exit are therefore comparable to each other but neither is a
verification of the cut.

The first `mvn verify` after the step failed. See [Bug 36](#bug-36-verify-caught-what-mvn-test-could-not-skip-into)
below; measured after the fix, surefire is 241 / 0 / 0 / 0 and failsafe 13 / 0 / 0 / 0, with
`./mvnw -B clean verify` at exit 0.

#### Bug 36: `verify` caught what `mvn test` could not skip into

`DashboardCacheRaceIT` created its habit with the two-argument constructor, so the row carried
`owner_id = NULL` while the test read the dashboard as owner `501`. The chain, in order:

1. `findActive(ownerId)` is now owner-scoped, so the `NULL`-owner row is invisible to `501`
2. `activeHabitIds` is empty
3. `HabitQueryService` short-circuits — `activeHabitIds.isEmpty() ? Map.of() : findLatestByHabitIds(...)`
4. the test's `@Around` aspect is bound to `findLatestByHabitIds`, so it never fires
5. `snapshotRead.countDown()` never runs and the 5-second `await` returns `false`

**The failure message described step 5; the defect was step 1.** Not a race, not a flake —
deterministic. Its sibling `HabitDashboardCacheIT` passes because it mocks `findActive`, so it never
touches the column that changed: same feature, two tests, only the one using a real repository felt
step 4.

Fixed by provisioning the owner through `TestApiClientOwner.ensureExists` before the save — the order
matters, `fk_habits_owner` is `ON DELETE RESTRICT` — and by prepending `ownerId` to both cache keys
the test constructs, to match `DashboardCacheKeyGenerator`. Same class as the `-DskipTests` trap: green
from **absence of execution**, not from success. The difference is that here CI caught what the local
command skipped.

## Traps found in review

### Every completion-table access inherits ownership rather than asserting it

`HabitCompletionRepository` and `HabitCompletionStatRepository` are untouched by step 4. Every call
into them is keyed by `habit_id` alone. Enumerated from `src/main/java` — five call sites guarded by a
preceding owner-scoped habit lookup, plus dashboard stats, which the ADR covers explicitly:

| Caller | Guard that runs first | Completion access |
| --- | --- | --- |
| `HabitQueryService.getHistory` | `existsById(ownerId, habitId)` | `findByHabitIdAndCompletedOnBetweenOptional` |
| `HabitQueryService.getCompletionRate` | `findById(ownerId, habitId)` | `findCompletedDatesInPeriod(habitId, …)` |
| `HabitQueryService.getStatsProjection` | `findById(ownerId, habitId)` | `findFirstByHabitIdOrderByCompletedOnDesc`, `findStatsByHabitId` |
| `HabitCommandService.complete` | `findById(ownerId, habitId)` | `save(new HabitCompletion(habitId, today))` |
| `HabitCommandService.uncomplete` | `findById(ownerId, habitId)` | `deleteByHabitIdAndCompletedOn`, `findByHabitIdOrderByCompletedOnDesc` |
| `HabitQueryService.getDashboardStats` | `findActive(ownerId)` | `findLatestByHabitIds(activeHabitIds)` |

Only the last row matches the ADR: it passes an ID set already selected for the owner, which is the
mechanism the ADR names. The other five do not, and they contradict two sentences of it — "every read,
insert, update and delete includes the owner ID in its database operation. A preceding ownership check
alone is insufficient", and, more specifically, "completion history, completion rate and per-habit
statistics enforce ownership through a join to `habits`". No such join exists; a preceding lookup was
written instead.

What actually protects these five is `fk_habit_completions_habit` (V8) plus the fact that `habits` is
now owner-scoped: a `habit_id` obtained through an owner-scoped read cannot belong to another owner.
The guard and the query also sit in one transaction, so there is no window between them.

**This is inheritance, not assertion, and it is the kind of protection worth naming.** The line that
looks like it carries the guarantee — the `existsById` / `findById` check — is not what holds; the
foreign key is. Delete the guard and these paths still cannot cross owners, because there is no
reachable `habitId` that would let them.

Accepted as-is. Asserting ownership directly needs either an owner column on the completion tables,
which the ADR rejects to keep `habits` the single source of ownership, or the join the ADR asked for.
Revisit if any completion access is ever reached without first resolving the habit through an
owner-scoped statement — that is the condition under which inherited protection stops holding, and it
is not enforced by anything today.

### Kafka: the consumer inherits ownership through `habitId`

`HabitCompletedEventConsumer` (`@KafkaListener(topics = "habit-completed", groupId = "habit-stats")`)
writes to the read model outside any HTTP request, so the interceptor cannot reach it and there is no
authenticated client on that path.

It does not need one. `HabitCommandService` publishes the event only after the authenticated command
path has resolved an owned habit through `findById(ownerId, habitId)`, so ownership is already decided
upstream of the topic. The consumer writes `habit_completion_stats` keyed by `habit_id`, and `habits`
remains the single ownership source of truth.

Adding `ownerId` to the event payload was rejected for this step: `HabitEvent` is a sealed interface
deserialized by `JsonDeserializer<HabitCompletedEvent>`, so a new field is a breaking topic-format
change that does not describe already-queued messages, and it would duplicate ownership the habit
foreign key already represents. Same class as the three paths above — inherited, not asserted.

### The dashboard cache key generator throws on a shape mismatch, by design

`DashboardCacheKeyGenerator.generate` previously read `LocalDate today = (LocalDate) params[0]`.
Inserting `ownerId` as a new first argument would have turned that into a runtime
`ClassCastException`, so the positional contract was replaced rather than worked around by argument
order.

It now validates the shape up front — `params.length != 2`, `params[0] instanceof Long ownerId`,
`params[1] instanceof LocalDate today` — and throws `IllegalArgumentException` naming the expected
signature. The failure mode is deliberate: a future argument reorder fails loudly at the key
generator instead of silently producing a key that mixes owners.

Both generated forms are covered, and both must stay checked against argument reordering:

- normal Redis path: `ownerId::generation::today`
- generation-read failure: `bypass::UUID::ownerId::today`

The bypass UUID prevents cache reuse; it does not authorise anything. The underlying dashboard read
must be owner-scoped regardless of which key is produced — and is, through `findActive(ownerId)`.

### `TestApiClientOwner` is H2-only and does not say so

The step-4 helper `TestApiClientOwner.ensureExists` provisions an owner row with
`MERGE INTO api_clients … KEY (id)`. That is H2 syntax; MySQL does not accept it. Seven test classes
use it and all seven are H2. The two `MySqlIT` classes provision owners their own way —
`HabitCompletionConcurrencyMySqlIT` builds `new Habit(ownerId, …)` directly — so nothing is broken
today.

The name carries no dialect hint, so the first `MySqlIT` that reaches for it fails at runtime for a
reason its call site does not suggest. Same shape as the `spring.cache.type: none` trap below: green
until touched from another context.

Decided 2026-09-02: it stays as an explicitly test-only H2 helper, deferred to V18 rather than renamed
now. No production path is affected, and V18 `NOT NULL` forces every owner fixture to be revisited
anyway — including the two `MySqlIT` classes that currently provision owners their own way. Doing it
twice buys nothing. The obligation is therefore attached to V18 below, not left as a loose observation.

### The `ClientTier` branch in the argument resolver is unreachable from production

`supportsParameter` accepts `ClientContext.class` **or** `ClientTier.class`, and `resolveArgument`
returns `context.tier()` for the latter. After step 4 no `@ResolvedClientTier ClientTier` parameter
exists in `src/main/java`, so that branch is reached only from `ClientTierArgumentResolverTest`.

Step 3 set the opposite precedent in this same ADR — the dead `ClientTierResolver` path was deleted
rather than left unused, so that no annotation survives that no code reaches. The branch is kept here
as a resolver-level compatibility affordance; if it is not wanted, it should be removed together with
its test rather than left as a second supported shape nothing uses.

### `Habit(String, Instant)` now builds an ownerless habit

The two-argument constructor delegates to `this(null, name, createdAt)`, so `insert` writes
`owner_id = NULL` and the row is unreachable through the API.

This was first written up as a V18 item, reasoning that no production code calls it —
`HabitCommandService.create` uses the three-argument form. **That reasoning was true and irrelevant, and
Bug 36 disproved the boundary the same day.** 39 test call sites use the constructor, and owner scoping
on its own, with the column still nullable, was enough to break one of them. `NOT NULL` was never
required.

Where it stands now, enumerated rather than recalled:

| Callers | Reach the database | Status |
| --- | --- | --- |
| `HabitCommandServiceTest` (21), `HabitQueryServiceTest` (7), `HabitTest` (9), `HabitControllerTest` (1), `HabitResponseTest` (1) | no | safe — `owner_id` never persisted |
| `HabitCompletionRepositoryIntegrationTest` | yes | latent — queries by `habit_id`, never through `findActive`, so it passes |
| `DashboardCacheRaceIT` | yes | **broke**, fixed 2026-09-02 |

So the hazard is not a future migration failure; it is any test that saves through this constructor and
then reads through an owner-scoped statement. Fixing only the one IT that failed closes nothing — the
next such test fails identically. The open decision is delete the constructor now, or keep it with a
test that pins it as unit-only; either way it is a today decision, not a V18 one. It is listed under
V18 below **only** because `NOT NULL` makes it an insert failure as well, which is a second reason, not
the first.

This is the mirror image of "correct for an unstated reason": a claim that something is harmless until a
future migration, where the protection was in fact already absent.

### Owner-scoped cache tests need an explicit cache type

`src/test/resources/application.yml` sets `spring.cache.type: none` globally, and the dashboard
`@Cacheable` condition disables caching when the type is not `redis`. A test that intends to prove
cache isolation between owners will pass vacuously under the default configuration, because no cache
entry is ever created. Such tests must set `spring.cache.type=redis` explicitly.

### Integration-test teardown breaks on the foreign key (fixed in step 2)

`ON DELETE RESTRICT` means an `ApiClient` cannot be deleted while it owns habits. Two teardown paths
deleted clients directly and would fail with MySQL error `1451` once habits carry owners:

- `PrometheusConfigurationIntegrationTest.deleteSavedClient` — `apiClientRepository.delete(savedClient)`
- `ClientTierCacheIT.clearState` — `repository.deleteAll()` (this class was itself deleted in step 3
  together with the cache path it covered, so only the Prometheus teardown remains)

The surviving path deletes `habit_completion_stats` → `habit_completions` → `habits` → `api_clients`.
The inner ordering is load-bearing too, not defensive padding: `fk_habit_completions_habit` from V8
blocked `DELETE FROM habits` while completions existed, which was measured separately from the owner
constraint.

The full suite passing on V17 was **not** evidence that this was fine. Measured against V17 as written:
while every `owner_id` is `NULL`, deleting a client succeeds, because no habit row references it — the
foreign key is present but unstressed. As soon as one habit carries an owner, the same delete is
blocked (H2 `23503`, MySQL `1451`). The failure therefore appears at the step that assigns owners, not
at the step that adds the constraint, and a green run before step 4 cannot detect it. The fix was
verified by a probe that created a client, a habit carrying its `owner_id`, and then ran the teardown —
green under the new order, `23503` under the old one.

## Deferred to V18

`owner_id` becomes `NOT NULL` only after a verification query proves no nulls remain:

```sql
SELECT COUNT(*) FROM habits WHERE owner_id IS NULL;
```

Backfill is an explicit per-environment operation, not part of a migration. The owner is selected by
unique `api_key_hash`, never by an assumed numeric ID. In the measured local environment all 229
legacy rows map to the `Local dev` client. An environment whose legacy rows belong to more than one
client needs a per-row mapping and cannot use the single-owner backfill.

Two step-4 items are deferred here rather than to a separate cleanup, because `NOT NULL` forces both:

- **`Habit(String, Instant)`** must be deleted or made unusable. It yields `owner_id = NULL`, which
  V18 turns from an unreachable row into an insert failure. V18 is the deadline, not the trigger — it
  already broke one integration test under step 4 alone (Bug 36 above), so the decision is open now.
- **Owner fixtures must be unified across dialects.** `TestApiClientOwner` is H2-only
  (`MERGE INTO ... KEY (id)`) while the two `MySqlIT` classes provision owners their own way. V18
  removes the option of an unowned habit, so every test that creates a habit needs a working owner on
  both dialects. That is the point at which one helper covering both is worth writing; renaming it
  before then would be redone here.

Do not read the current green suite as evidence that these are safe. It is green precisely because
`owner_id` is still nullable — the same reason a green run before step 4 could not detect the teardown
ordering failure above.
