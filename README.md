# Habit Tracker

Event-driven habit tracking app. Side project for the 100-day BE plan.

## Stack (by phase)

- **F1 (d1-21):** Spring Boot 3, MySQL 8 (prod) / H2 in-memory (test), Flyway
- **F2 (d22-50):** + Kafka producer/consumer, async stats, idempotency
- **F3 (d51-78):** + Redis, concurrency challenges, JFR profiling, GitHub Actions CI
- **F4 (d79-100):** + Prometheus

## Prerequisites

- Java 21
- Maven (via the `./mvnw` wrapper)
- Docker (for a prod-style local run and the full `./mvnw verify` test suite; the fast `./mvnw test` suite does not need Docker)

## Run locally

```bash
docker compose up -d        # MySQL 8.0 (3306), Kafka (9092), Redis 7 (6379)
./mvnw spring-boot:run      # app on http://localhost:8080
```

Flyway applies the migrations from `src/main/resources/db/migration/` on startup. Hibernate runs in `validate` mode — it checks that entities match the tables and never changes the schema.

Stop:
```bash
docker compose down         # stop the infra (named volume habits_data keeps the MySQL data)
docker compose down -v      # also drop the volume for a clean database
```

## Run tests

```bash
./mvnw test          # fast suite — unit + H2 + Embedded Kafka, no Docker needed
./mvnw verify        # full suite — also runs Redis + MySQL Testcontainers tests, requires Docker
```

The fast suite runs on **H2 in-memory** in MySQL-compatible mode (`MODE=MySQL`) and does not need Docker. The Spring context starts in ~3s, Flyway migrates H2, and each run gets a clean database.

Docker-dependent tests (Redis and MySQL via Testcontainers) are named `*IT` and run under the Maven Failsafe plugin in the `verify` lifecycle. Without Docker, `./mvnw verify` **fails loudly** instead of silently skipping them — a green `verify` always means the integration tests actually ran. Note that `./mvnw integration-test` is not a substitute for `verify`: Failsafe records failures during `integration-test` but only fails the build in the `verify` goal.

## CI

`.github/workflows/ci.yml` runs `./mvnw verify` on every push and pull request to `main`. Because `verify` fails loudly without Docker, a green CI run is proof that the Redis and MySQL Testcontainers tests actually executed — the gate cannot pass by skipping them. A `docker info` preflight step fails fast with a readable error if the runner has no daemon, instead of surfacing it as a Testcontainers bootstrap failure minutes later.

## API

- Full endpoint specification: [docs/api-reference.md](docs/api-reference.md)
- Quick curl examples: [curls.md](curls.md)

## Design decisions

- Architectural decisions: [docs/adr/](docs/adr/README.md)
- Implementation notes for decisions still in progress: [docs/implementation/](docs/implementation/)

## Deploy (minikube)

The Helm chart in `deploy/habit-tracker/` runs the whole stack in-cluster — app and Redis as Deployments, MySQL and Kafka as StatefulSets with their own PVCs, and an init container that waits for MySQL before the JVM starts.

**The chart's default `image.tag` is `latest`, and no such image is ever built.** A plain `helm install` with default values renders `habit-tracker:latest` and the pod fails trying to pull it from Docker Hub. This is not a broken chart — the image is built locally into minikube's own Docker daemon under the tag `dev`, and both the tag and the pull policy are supplied at install time:

```bash
eval $(minikube docker-env)              # build into minikube's daemon, not the Mac one
docker build -t habit-tracker:dev .
helm install ht deploy/habit-tracker \
  --set image.tag=dev \
  --set image.pullPolicy=Never          # never reach for a registry; the image is already local
```

`image.tag=dev` with `pullPolicy=Never` is deliberate rather than incidental: a mutable `latest` combined with the default `IfNotPresent` silently keeps running whatever stale layer already sits in the node's image cache, so a rebuild appears to deploy without the new code ever starting.

Object names are `<release>-habit-tracker[-component]`, so with release `ht` the app Service is `ht-habit-tracker`, not `ht`:

```bash
kubectl get pods                                    # ht-habit-tracker, -redis, -mysql-0, -kafka-0 all 1/1
kubectl port-forward service/ht-habit-tracker 8080:8080
curl -s localhost:8080/actuator/health
```

Tear down:
```bash
helm uninstall ht
kubectl get pvc                                     # StatefulSet PVCs outlive uninstall by design
kubectl delete pvc mysql-data-ht-habit-tracker-mysql-0 kafka-data-ht-habit-tracker-kafka-0
```

`volumeClaimTemplates` PVCs carry no chart labels, so there is no `-l app.kubernetes.io/instance=ht` selector to delete them by — list first, then delete by name. Leaving them behind is how a fresh `helm install` comes up with the previous run's data still in place.

### GitOps (ArgoCD)

`deploy/argocd/habit-tracker-application.yaml` is the same chart under ArgoCD, tracking `deploy/habit-tracker` on `main`. It passes those two image parameters through `helm.parameters`, which is the only reason the committed defaults never surface in that path:

```bash
kubectl apply -f deploy/argocd/habit-tracker-application.yaml
```

`syncPolicy.automated` has both `prune` and `selfHeal` on, so manual `kubectl` edits to a managed resource are reverted to the Git state rather than kept.

Two details worth knowing before reading the chart:
- **`Chart.yaml`'s `appVersion` is effectively dead as an image tag.** The template falls back to it (`image.tag | default .Chart.AppVersion`), but `values.yaml` always supplies a non-empty tag, so the fallback only fires if the tag is explicitly overridden to empty.
- **Secrets are not modelled.** The MySQL password is passed as a literal in `values.yaml` and appears on the init container's command line. Acceptable for a local minikube run, not for a real cluster.

## Architecture

One Spring Boot service. A write is committed to MySQL first; everything derived from it (the Kafka stream, the stats projection, the dashboard cache) is updated **after commit**, so nothing downstream can observe a write that later rolls back. The trade-off runs the other way: there is no outbox, so if the process stops between the commit and the Kafka send, the committed event is never published and the stats projection misses it.

```
request ──► ClientAuthenticationInterceptor ──► HabitController
            (API key hash → owner + tier,          │
             uncached DB lookup)                   ├──► HabitCommandService / HabitSkipService ──► MySQL
                                                   │        │ publishes Spring events in the transaction
                                                   │        ▼  AFTER_COMMIT
                                                   │    HabitEventKafkaPublisher ──► topic habit-completed
                                                   │    DashboardCacheInvalidator ──► Redis (generation++, clear)
                                                   │
                                                   └──► HabitQueryService ──► MySQL + Redis (GET /habits/stats)

topic habit-completed ──► HabitCompletedEventConsumer (group habit-stats)
                          writes habit_completion_stats ──► DashboardChangedEvent ──► invalidator
```

- **Identity and scope.** Every `/habits/**` request passes `ClientAuthenticationInterceptor`, which resolves the API key hash to a `ClientContext` (owner and `ClientTier`). The owner scopes every habit query; the tier only shapes the response (`HabitResponseTransformer`), it does not gate operations. See [ADR 0001](docs/adr/0001-canonical-api-key-hash-format.md) and [ADR 0005](docs/adr/0005-scope-habits-to-api-client-owners.md).
- **Commands and queries.** Writes go through `HabitCommandService` and `HabitSkipService`, reads through `HabitQueryService` ([ADR 0002](docs/adr/0002-separate-command-query-services.md)). It is CQS-oriented, not strict CQS: commands may return the updated habit.
- **Two persistence styles, one database.** The `habits` table is mapped by MyBatis (`HabitMapper.xml`, `Habit` is a plain class), while completions, skips, the stats projection and API clients are JPA entities. Flyway owns the schema for both; Hibernate only validates it.
- **Event flow.** Completing or uncompleting a habit publishes a `HabitCompletedEvent` / `HabitUncompletedEvent`; after commit it is sent to the `habit-completed` topic, keyed by habit id. Despite the topic name, it carries both event types. The consumer maintains the `habit_completion_stats` read model and treats a duplicate completion as already applied (unique constraint), so redelivery is safe.
- **Dashboard cache.** `GET /habits/stats` is cached in Redis under `ownerId::generation::today` with a 5-minute TTL. Both the write side and the consumer publish `DashboardChangedEvent`; the invalidator advances the generation after commit, so a request that was in flight during the write cannot repopulate a stale entry under the new key ([ADR 0003](docs/adr/0003-invalidate-dashboard-cache-from-both-sides.md), including the per-path failure table). Operational details are under [Cache](#cache).
- **Metrics.** Actuator exposes `health`, `metrics` and `prometheus`; `HabitCompletionMetrics` counts completions by outcome (first attempt, retried, conflict exhausted).

## Project layout

```
src/main/java/com/nantonijevic/habits/
  HabitTrackerApplication.java   # Spring Boot entry point
  client/                        # API key authentication, owner + tier context, tier-shaped responses
  controller/                    # REST endpoints (HabitController)
  service/                       # HabitCommandService + HabitSkipService (writes), HabitQueryService (reads), metrics
  domain/                        # Habit (MyBatis-mapped) + JPA entities + domain exceptions
  dto/                           # request/response records
  repository/                    # MyBatis mapper for habits, Spring Data JPA for the rest
  event/                         # domain events, Kafka publisher/consumer
  cache/                         # dashboard cache: generation key, invalidator, cache failure policy
  config/                        # Kafka producer/consumer, Redis cache, clock
  exception/                     # GlobalExceptionHandler
src/main/resources/
  application.yml                # MySQL datasource (prod) + Kafka
  db/migration/                  # Flyway migrations
  mappers/HabitMapper.xml        # MyBatis statements for the habits table
src/test/resources/
  application.yml                # H2 in-memory (test)
```

## Database

- **Prod (local run):** MySQL 8.0 via docker-compose, data persisted in the named volume `habits_data`.
- **Test (fast suite):** H2 in-memory with `MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE`. Flyway runs the same migrations.
- **Test (`verify` only):** `HabitCompletionConcurrencyMySqlIT` runs against a real `mysql:8.0.36` container. H2 in MySQL mode cannot prove InnoDB behaviour — `REPEATABLE READ` snapshot semantics (why the completion retry needs a *new* transaction) and the `V12` unique constraint on the production engine are only observable here. The image tag is pinned so the proof does not drift.

## Cache

- **Redis 7** via docker-compose caches the dashboard stats (`GET /habits/stats`) with a 5-minute TTL.
- `maxmemory-policy` is pinned to `noeviction` in `docker-compose.yml` — the versioned generation key must never be evicted; this is a deliberate infrastructure decision, not a default.
- Reads fall back to the database: a Redis `DataAccessException` on cache GET/PUT or on the generation read is logged as a WARN and the request is served from MySQL — no user-facing failure. Other exceptions and invalidation failures follow different rules; the full table is in [ADR 0003](docs/adr/0003-invalidate-dashboard-cache-from-both-sides.md).
- No volume on purpose: the cache is derived data, the database stays the source of truth.
- The compose app reaches Redis via `SPRING_DATA_REDIS_HOST=redis`; a host-run app uses the published `localhost:6379` port (Spring Boot default).
- **Test:** the `*IT` integration tests (run via `./mvnw verify`) start their own Redis via Testcontainers and do not need the compose Redis.

Configuration:
- Prod: `src/main/resources/application.yml`
- Test: `src/test/resources/application.yml`
