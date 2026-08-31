# Implementation notes: ADR 0005 habit ownership

Companion to [ADR 0005](../adr/0005-scope-habits-to-api-client-owners.md). The ADR records the
decision; this file records the ordering constraints and the traps found while reviewing it, so they
are not rediscovered during implementation.

Status: V17 applied. Steps 2 onwards are not started.

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

### 2. Migrate the existing test suite to an authenticated client

**This must precede any ownership test.** Seven test classes exercise `/habits`; five send no API key
at all:

| Test class | Sends `X-Api-Key` |
| --- | --- |
| `HabitControllerIntegrationTest` | no |
| `HabitStatsIntegrationTest` | no |
| `HabitStreakConsistencyIntegrationTest` | no |
| `HabitCompletionConcurrencyIntegrationTest` | no |
| `HabitCompletionConcurrencyMySqlIT` | no |
| `ClientTierFilteringIntegrationTest` | yes |
| `PrometheusConfigurationIntegrationTest` | yes |

Once the interceptor is in place, those five fail with `401` before reaching any business logic. The
failures would be uninformative: they would report an authentication problem in tests written to
assert streaks, statistics and optimistic locking. Provision a client and add the header first, while
the suite is still green for the right reason.

The header name is `X-Api-Key`, from `ClientTierArgumentResolver.API_KEY_HEADER`.

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

### Integration-test teardown will break on the foreign key

`ON DELETE RESTRICT` means an `ApiClient` cannot be deleted while it owns habits. Two teardown paths
delete clients directly and will fail with MySQL error `1451` once habits carry owners:

- `PrometheusConfigurationIntegrationTest.deleteSavedClient` — `apiClientRepository.delete(savedClient)`
- `ClientTierCacheIT.clearState` — `repository.deleteAll()`

Delete owned habits before deleting their client.

The full suite passing on V17 is **not** evidence that this is fine. Measured against V17 as written:
while every `owner_id` is `NULL`, deleting a client succeeds, because no habit row references it — the
foreign key is present but unstressed. As soon as one habit carries an owner, the same delete is
blocked (H2 `23503`, MySQL `1451`). The failure therefore appears at the step that assigns owners, not
at the step that adds the constraint, and today's green run cannot detect it.

## Deferred to V18

`owner_id` becomes `NOT NULL` only after a verification query proves no nulls remain:

```sql
SELECT COUNT(*) FROM habits WHERE owner_id IS NULL;
```

Backfill is an explicit per-environment operation, not part of a migration. The owner is selected by
unique `api_key_hash`, never by an assumed numeric ID. In the measured local environment all 229
legacy rows map to the `Local dev` client. An environment whose legacy rows belong to more than one
client needs a per-row mapping and cannot use the single-owner backfill.
