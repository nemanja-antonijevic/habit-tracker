# ADR 0005: Scope habits to authenticated API client owners

- Status: Accepted
- Date: 2026-08-29

## Context

All habits currently belong to one global data set. `HabitMapper.xml` reads and writes `habits` without an owner predicate: `findById` uses only `id`, `findActive` returns every non-archived habit, and the single insert statement writes no owner. The 16 endpoints under `/habits` therefore operate over shared state.

API clients already have a stable database identity. `ApiClient.id` is the primary key of `api_clients`, while `api_key_hash` is a unique credential and `tier` controls response-field visibility. However, `ClientTierResolver.resolve` currently returns only `ClientTier`. A missing API key returns anonymous `PUBLIC` without a database lookup, so the current resolver cannot identify an owner.

Adding a non-null owner column in one migration is unsafe on existing data. This was measured on MySQL with 229 `habits` rows and `STRICT_TRANS_TABLES` enabled. Adding `owner_id BIGINT NOT NULL` without a default succeeded silently and assigned `0` to all 229 rows. Adding a foreign key from those values to `api_clients.id` then failed with error `1452`. This differs from the V16 `3819` check-constraint failure: here the dangerous first operation succeeds and creates semantically invalid ownership.

Dashboard caching is also global. `DashboardCacheKeyGenerator` currently emits `generation::today`; on a generation-read `DataAccessException` it emits `bypass::UUID::today`. `HabitQueryService.getDashboardStats` bypasses caching entirely when `spring.cache.type` is not `redis`.

Completion history and completion statistics have no independent owner identity. `habit_completions` and `habit_completion_stats` are reached through `habit_id`, and their repositories operate on that relationship.

## Decision

Use `api_clients.id` as the owner identity for habits. The API key hash remains a credential of that identity; it is not itself stored as the owner.

Replace tier-only resolution with an authenticated client context containing at least `clientId` and `tier`. A provisioned client with tier `PUBLIC` still has an identity and can own habits. A request without an API key has no owner identity and must receive `401 Unauthorized` on owner-scoped `/habits` endpoints. Anonymous `PUBLIC` access must not be mapped to a shared or synthetic owner.

Introduce ownership through an expand/backfill/contract sequence:

1. V17 adds nullable `habits.owner_id`, an index, and a foreign key to `api_clients.id` with delete restricted.
2. Application reads and writes are scoped by the resolved client ID. Rows whose owner is still null are not exposed to ordinary clients.
3. Existing rows are explicitly assigned to the intended provisioned client through an environment-specific backfill. The migration must not invent owner `0` or guess an owner.
4. A later migration makes `owner_id` non-null only after a verification query proves that no null values remain.

Deleting an API client must not cascade-delete its habits. Once `api_clients.id` becomes an owner identity, revocation must preserve the row and disable or rotate its credential. Deletion-based revocation, currently exercised by cache tests and debug probes rather than a production write path, cannot become the ownership lifecycle.

All habit reads, updates and deletes must include the owner predicate, not only a preceding ownership check. This prevents a check/use gap and prevents access to another client's habit by guessing its numeric ID.

Dashboard data remains cached, but the normal cache key becomes owner-scoped:

`ownerId::generation::today`

The generation may initially remain global. A change by one owner can therefore invalidate dashboard entries for every owner, which is inefficient but safe. Per-owner generations are a later optimisation, not an isolation requirement.

The other two cache paths remain safe for different reasons:

- The `bypass::UUID` path does not reuse an entry, but its underlying dashboard query must still be owner-scoped. UUID uniqueness is not an authorisation mechanism.
- When the `@Cacheable` condition is false, no cache key exists. Isolation is provided entirely by owner predicates in the database queries.

`habit_completions` and `habit_completion_stats` do not receive duplicate owner columns. Their ownership is derived by joining through `habits.id`, which remains the single ownership source of truth.

## Alternatives considered

A free-form owner string was rejected. It would create a second identity namespace beside `api_clients`, provide no referential integrity, and require a separate trusted mapping between a credential and an owner string. If accepted directly from a request, it would be caller-controlled rather than authenticated ownership.

Assigning every existing row to a synthetic default owner was rejected. The MySQL probe demonstrated the accidental version of this design as owner `0`. Creating a valid synthetic client would make unrelated legacy data appear to belong to one identity and would hide an ownership decision inside a migration. The chosen staged migration costs an operational backfill and an additional schema migration, but makes that decision explicit.

Keeping anonymous requests as one shared `PUBLIC` owner was rejected. It would turn all unauthenticated callers into the same tenant and preserve global data sharing under a different name.

Copying `owner_id` into `habit_completions` and `habit_completion_stats` was rejected. It could make some reads cheaper, but every event, completion and ownership transfer would then have to keep three tables consistent. Joining through `habits` adds query cost but prevents ownership drift.

## Consequences

Ownership becomes an authentication concern, not only response transformation. The current `ClientTierResolver` and `ClientTierLookup` must evolve from returning a tier to returning a client context. The `api-client-tiers` cache value and its typed Redis serializer must change accordingly.

All 16 `/habits` endpoints and all relevant MyBatis statements require owner propagation. Inserts write `owner_id`; reads, updates and deletes constrain it. Tests must include two clients and prove both positive access and cross-owner denial.

Anonymous requests that currently receive filtered `PUBLIC` responses will instead receive `401` on owner-scoped habit endpoints. This is an intentional breaking contract change.

Legacy rows may be temporarily invisible between the expand deployment and their explicit backfill. That availability cost is preferred over assigning them to the wrong client or exposing them across clients.

Queries over completion history and statistics gain joins to `habits` when ownership must be checked. This adds database work and requires appropriate indexes, but ownership remains defined in one place.

Global dashboard generation means one client's mutation can invalidate every client's dashboard entry. It cannot cause cross-owner data reuse because owner ID is part of the reusable cache key. If invalidation volume becomes material, generation can later be partitioned by owner without changing the ownership model.
