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

Redis failures remain fail-open: the database change succeeds and stale cache data may remain until TTL expiry. Advancing the generation before clearing is deliberate—if `clear()` fails, entries from the previous generation are no longer reachable by new requests.

A missing configured dashboard cache currently causes `cacheManager.getCache()` to produce an `IllegalStateException` outside the handled `DataAccessException` path. Because this occurs after commit, it cannot roll back the business transaction, but this fail-open outcome is incidental rather than explicitly handled.
