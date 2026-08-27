# ADR 0004: Accept stale client tiers bounded by TTL, with no eviction on write

- Status: Accepted
- Date: 2026-08-27

## Context

`ClientTierLookup.resolveByHash` is annotated `@Cacheable` on the `api-client-tiers` cache, keyed by the API key hash. `ApiClientRepository` writes are not paired with any eviction: `src/main` contains no `@CacheEvict` operation, and the only eviction in the application is the programmatic `cache.clear()` in `DashboardCacheInvalidator`, which targets the dashboard cache.

The consequence is that changing or revoking a client's tier does not take effect immediately. The old tier stays in effect until the cache entry expires. `RedisCacheConfig` sets `API_CLIENT_TIERS_TTL` to one minute, against five minutes for the dashboard, and the comment above it states that tier changes, especially revocation, must propagate faster than derived dashboard data. The shorter TTL was therefore chosen deliberately, but it was chosen as a cache expiry value rather than derived from a revocation requirement.

Two properties of the current system bound the exposure, and both are measured rather than assumed.

The tier shapes the response only; it does not gate operations. `ClientTier` carries three booleans — `exposesScheduledDays`, `exposesArchived`, `exposesCreatedAt` — with `INTERNAL(true, true, true)`, `TRUSTED(true, true, false)` and `PUBLIC(false, false, false)`. `HabitResponseTransformer` uses them to return either the source value or `null` per field. `HabitController` passes the resolved tier to the transformer at nine call sites and branches no business logic on it. A stale `INTERNAL` tier therefore discloses three additional response fields; it does not authorise an operation the caller could not otherwise perform. The correct classification is information disclosure, not privilege escalation.

The service is not reachable from outside the cluster. `service.type` is `ClusterIP`, `ingress.enabled` and `httpRoute.enabled` are both `false`, and the chart contains no template that renders an `Ingress` or an `HTTPRoute` at all — the only consumer of those values is `NOTES.txt`. Enabling `ingress.enabled` would change the post-install console output and expose nothing; the blocks are leftovers from the `helm create` skeleton. The ArgoCD application deploys in-cluster to the `default` namespace.

## Decision

Accept staleness of up to the `api-client-tiers` TTL for tier changes and revocations. Do not add eviction on `ApiClientRepository` writes at this time.

The acceptance is conditional on both properties above. It stops holding if either of the following changes:

- The tier begins to gate operations rather than response fields, which turns stale-tier exposure from information disclosure into privilege escalation.
- The service becomes reachable from outside the cluster — a `Service` of type `LoadBalancer` or `NodePort`, or a real `Ingress` or `HTTPRoute` template added to the chart.

Record separately, and explicitly not as an accepted design, that TTL expiry is currently the only mechanism that ends a revoked tier's effect. Eviction on write is the correct mechanism for revocation, with TTL as a backstop rather than the primary control. That change requires a write path to evict from and is a separate piece of work with its own scope.

## Alternatives considered

Evicting on `ApiClientRepository.save` and `delete` is the mechanism this ADR defers, not one it rejects. It is not implemented today because `src/main` has exactly one consumer of the repository — `ClientTierLookup`, using only `findByApiKeyHash` — so there is no production write path to attach eviction to. Adding `@CacheEvict` now would introduce an annotation that no code reaches, and the reachability of an untriggered guard is precisely the class of defect ADR 0003 documents for the missing-cache branch.

Shortening the TTL further would narrow the window without addressing the mechanism. It also trades a fixed database lookup cost against an exposure window that revocation should close immediately rather than sooner.

Caching negative lookups was rejected for a different reason and does not belong to this decision. `resolveByHash` throws `InvalidApiKeyException` before `@Cacheable` has a value to store, so unknown keys always reach the database. Combined with the absence of rate limiting anywhere in `src/main` or the chart, that is an amplification concern rather than a staleness one, and it is bounded by the same absence of external reachability.

## Consequences

A revoked or downgraded API key keeps its previous tier for up to one minute. During that window the client receives the response fields its old tier exposed.

The bound is a cache expiry value, not a revocation guarantee. Nothing in the code enforces a maximum propagation delay for a security-relevant change; raising `API_CLIENT_TIERS_TTL` would silently widen the exposure window, because no test asserts an upper bound on revocation latency.

Both conditions that make this acceptable are outside the Java source. Reviewing `src/main` alone is not sufficient to confirm that this decision still holds: the second condition lives in `deploy/habit-tracker/values.yaml` and the chart templates, and a change there would invalidate the decision without any Java diff.
