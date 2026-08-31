# Implementation notes: ADR 0005 habit ownership

Companion to [ADR 0005](../adr/0005-scope-habits-to-api-client-owners.md). The ADR records the
decision; this file records the ordering constraints and the traps found while reviewing it, so they
are not rediscovered during implementation.

Status: V17 applied, test suite authenticated. Steps 3 onwards are not started.

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

### 3. Interceptor before controller changes

`ClientTierArgumentResolver.supportsParameter` requires the `@ResolvedClientTier` annotation, so
resolution is opt-in per parameter. `HabitController` has 16 endpoints and only 9 declare that
parameter; the other 7 (bulk complete, delete, stats, history, completion rate, due-today count,
dashboard stats) are unreachable by the resolver. Authentication that lives there cannot cover 44% of
the controller, which is why ADR 0005 moves the boundary to a `HandlerInterceptor` over `/habits` and
`/habits/**`.

After the interceptor exists, the argument resolver becomes a reader of the request attribute it
sets, not the component deciding whether authentication runs.

### 4. Owner scoping in SQL, not above it

`HabitMapper.xml` has eight statements over `habits` — `findById`, `existsById`, `deleteById`,
`findActive`, `insert`, `update`, `search`, `count` — plus the shared `searchWhere` fragment used by
`search` and `count`. Every one needs the owner in the statement itself. A check performed before the
call is not equivalent: it leaves a window and it does not protect callers that bypass the check.

## Traps found in review

### Kafka is a second write path with no client context

`HabitCompletedEventConsumer` (`@KafkaListener(topics = "habit-completed", groupId = "habit-stats")`)
writes to the read model outside any HTTP request. The interceptor cannot reach it, so there is no
authenticated client to attribute the work to. Decide explicitly how the consumer obtains ownership —
carry the owner in the event payload, or derive it by joining through `habit_id` — rather than
discovering the gap when statistics silently cross owners.

### The dashboard cache key generator will throw, not misbehave

`DashboardCacheKeyGenerator.generate` reads `LocalDate today = (LocalDate) params[0]`. Inserting
`ownerId` as a new first method argument turns that line into a runtime `ClassCastException`. The
positional contract has to be replaced with named or typed values, not worked around by argument
order.

Both generated forms need tests, and both must be checked against argument reordering:

- normal Redis path: `ownerId::generation::today`
- generation-read failure: `bypass::UUID::ownerId::today`

The bypass UUID prevents cache reuse; it does not authorise anything. The underlying dashboard read
must be owner-scoped regardless of which key is produced.

### Owner-scoped cache tests need an explicit cache type

`src/test/resources/application.yml` sets `spring.cache.type: none` globally, and the dashboard
`@Cacheable` condition disables caching when the type is not `redis`. A test that intends to prove
cache isolation between owners will pass vacuously under the default configuration, because no cache
entry is ever created. Such tests must set `spring.cache.type=redis` explicitly.

### Integration-test teardown breaks on the foreign key (fixed in step 2)

`ON DELETE RESTRICT` means an `ApiClient` cannot be deleted while it owns habits. Two teardown paths
deleted clients directly and would fail with MySQL error `1451` once habits carry owners:

- `PrometheusConfigurationIntegrationTest.deleteSavedClient` — `apiClientRepository.delete(savedClient)`
- `ClientTierCacheIT.clearState` — `repository.deleteAll()`

Both now delete `habit_completion_stats` → `habit_completions` → `habits` → `api_clients`. The inner
ordering is load-bearing too, not defensive padding: `fk_habit_completions_habit` from V8 blocks
`DELETE FROM habits` while completions exist, which was measured separately from the owner constraint.

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
