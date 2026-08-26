# ADR 0003: Invalidate the dashboard cache from both source models

- Status: Accepted
- Date: 2026-07-20

## Context

The dashboard combines active habits from the transactional write model with streak data from the Kafka-maintained `habit_completion_stats` read model. Invalidating the cache only when the Kafka consumer updates the projection would miss write-side changes such as create, update, archive, unarchive and delete.

Invalidating before a database transaction commits is also unsafe. A concurrent dashboard request could read the old committed state after eviction, repopulate the cache with that value, and leave it cached after the command commits.

## Decision

Publish `DashboardChangedEvent` from both command operations and the Kafka consumer. Handle the event with an `AFTER_COMMIT` transactional listener so invalidation happens only after the corresponding database state is committed.

Invalidation first advances the dashboard cache generation and then clears the previous cache entries. New requests therefore use the new generation even if clearing old entries fails.

## Alternatives considered

Invalidating only in the Kafka consumer would cover read-model changes but miss commands that affect only the write model.

Invalidating inside the transaction would reduce the delay before eviction but allow a concurrent request to repopulate stale data before commit.

Using only `cache.clear()` would be simpler, but it would not protect against an in-flight request writing an old result after eviction.

## Consequences

Every source that contributes to the dashboard must publish an invalidation event when its committed state changes.

Cache failure behavior is path-specific rather than uniformly fail-open:

| Path | Behavior | Reachable in production |
| --- | --- | --- |
| Intercepted GET or PUT throws `DataAccessException` | Fail-open: log a warning and continue without the cache operation | Yes |
| Programmatic dashboard invalidation throws `DataAccessException` while advancing the generation or clearing the cache | Fail-open: log a warning and continue after the business transaction has committed | Yes |
| Programmatic dashboard invalidation throws another `RuntimeException` | Fail-closed: rethrow from the listener after commit | Yes |
| Intercepted GET or PUT throws another `RuntimeException` | Fail-closed: rethrow the exception | Yes |
| `CacheErrorHandler` handles an evict or clear failure | Fail-closed: rethrow the exception | No |

The last row is structurally unreachable in the current application. Spring calls `handleCacheEvictError` and `handleCacheClearError` only through the cache interceptor's `@CacheEvict` path, while `src/main` contains no `@CacheEvict` operation. The programmatic `cache.clear()` in `DashboardCacheInvalidator` calls the `Cache` interface directly and is handled by the invalidator's local `DataAccessException` catch; it does not pass through `CacheErrorHandler`.

The local invalidation catch covers failures from both generation advancement and cache clearing. If advancement fails with a `DataAccessException`, the existing generation and cached data may remain reachable until TTL expiry. If clearing fails after advancement succeeds, entries from the previous generation may remain stored, but new requests no longer address them.

The missing-cache guard in `DashboardCacheInvalidator` is also unreachable with the current cache managers. `NoOpCacheManager` and the configured `RedisCacheManager` create caches on demand, and the Redis dashboard cache is additionally registered through `withCacheConfiguration`.

That guard would become reachable only if Redis cache creation on demand were disabled and the initial `dashboard-stats` cache configuration were removed. Under that altered configuration, the `AFTER_COMMIT` listener would throw `IllegalStateException` after the business row was already committed. The measured probe showed that the caller still returned normally while Spring logged the exception from transaction synchronization.

`DashboardCacheInvalidatorTest.missingDashboardCacheStillFailsLoudly` documents this defensive guard by mocking `CacheManager.getCache()` to return `null`. It does not protect a state reachable with either cache manager currently used by the application.
