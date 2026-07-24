# Habit Tracker

Event-driven habit tracking app. Side project for the 100-day BE plan.

## Stack (by phase)

- **F1 (d1-21):** Spring Boot 3, MySQL 8 (prod) / H2 in-memory (test), Flyway
- **F2 (d22-50):** + Kafka producer/consumer, async stats, idempotency
- **F3 (d51-78):** + Redis, concurrency challenges, JFR profiling
- **F4 (d79-100):** + GitHub Actions CI, Prometheus

## Prerequisites

- Java 21
- Maven (via the `./mvnw` wrapper)
- Docker (only for a prod-style local run; tests do not need Docker)

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
./mvnw test
```

Tests run on **H2 in-memory** in MySQL-compatible mode (`MODE=MySQL`) and do not need Docker. The Spring context starts in ~3s, Flyway migrates H2, and each run gets a clean database.

## API

- Full endpoint specification: [docs/api-reference.md](docs/api-reference.md)
- Quick curl examples: [curls.md](curls.md)

## Project layout

```
src/main/java/com/nantonijevic/habits/
  HabitTrackerApplication.java   # Spring Boot entry point
  controller/                    # REST endpoints (HabitController)
  service/                       # HabitService (orchestration)
  domain/                        # @Entity classes + domain exceptions
  dto/                           # request/response records
  repository/                    # Spring Data JPA
  event/                         # domain events, Kafka publisher/consumer
  cache/                         # dashboard cache: generation key, invalidator, fail-open policy
  config/                        # Kafka producer/consumer + Redis cache config
  exception/                     # GlobalExceptionHandler
src/main/resources/
  application.yml                # MySQL datasource (prod) + Kafka
  db/migration/                  # Flyway migrations
src/test/resources/
  application.yml                # H2 in-memory (test)
```

## Database

- **Prod (local run):** MySQL 8.0 via docker-compose, data persisted in the named volume `habits_data`.
- **Test:** H2 in-memory with `MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE`. Flyway runs the same migrations.

## Cache

- **Redis 7** via docker-compose caches the dashboard stats (`GET /habits/stats`) with a 5-minute TTL.
- `maxmemory-policy` is pinned to `noeviction` in `docker-compose.yml` — the versioned generation key must never be evicted; this is a deliberate infrastructure decision, not a default.
- Reads are **fail-open**: if Redis is down, the dashboard falls back to the database and logs a WARN — no user-facing failure.
- No volume on purpose: the cache is derived data, the database stays the source of truth.
- The compose app reaches Redis via `SPRING_DATA_REDIS_HOST=redis`; a host-run app uses the published `localhost:6379` port (Spring Boot default).
- **Test:** integration tests start their own Redis via Testcontainers and do not need the compose Redis.

Configuration:
- Prod: `src/main/resources/application.yml`
- Test: `src/test/resources/application.yml`
