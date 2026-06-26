# Habit Tracker

Event-driven habit tracking app. Side project za 100-day BE plan.

## Stack (po fazama)

- **F1 (d1-21):** Spring Boot 3, MySQL 8 (prod) / H2 in-memory (test), Flyway
- **F2 (d22-50):** + Kafka producer/consumer, async stats, idempotentnost
- **F3 (d51-78):** + Redis, concurrency challenges, JFR profiling
- **F4 (d79-100):** + GitHub Actions CI, Prometheus

## Prerequisites

- Java 21
- Maven (preko `./mvnw` wrapper-a)
- Docker (samo za prod-style lokalni run; testovi ne traže Docker)

## Run lokalno

```bash
docker compose up -d        # MySQL 8.0 na portu 3306
./mvnw spring-boot:run      # app na http://localhost:8080
```

Flyway primenjuje migracije iz `src/main/resources/db/migration/` na startup-u. Hibernate je u `validate` mode-u (proverava da entity ↔ tabela odgovaraju, ne menja šemu).

Stop:
```bash
docker compose down         # zaustavi MySQL (named volume habits_data čuva podatke)
docker compose down -v      # + obriši volume ako hoćeš čistu bazu
```

## Run testovi

```bash
./mvnw test
```

Testovi koriste **H2 in-memory** u MySQL kompatibilnom mode-u (`MODE=MySQL`). **Ne traže Docker** — Spring kontekst se diže za ~3s, Flyway migrira na H2, čista baza za svaki test run.

## API

Kompletna specifikacija svih endpointa: [docs/api-reference.md](docs/api-reference.md).
Brzi curl primeri: [curls.md](curls.md).

## Project layout

```
src/main/java/com/nantonijevic/habits/
  HabitTrackerApplication.java   # Spring Boot entry
  controller/                    # REST endpoints (HabitController)
  service/                       # HabitService (orkestracija)
  domain/                        # @Entity klase + domenski izuzeci
  dto/                           # request/response records
  repository/                    # Spring Data JPA
  event/                         # domenski eventi, Kafka publisher/consumer
  config/                        # Kafka producer/consumer config
  exception/                     # GlobalExceptionHandler
src/main/resources/
  application.yml                # MySQL datasource (prod) + Kafka
  db/migration/                  # Flyway migrations
src/test/resources/
  application.yml                # H2 in-memory (test)
```

## Database

- **Prod (lokalni run):** MySQL 8.0 kroz docker-compose, perzistencija u named volume `habits_data`.
- **Test:** H2 in-memory sa `MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE`. Flyway prolazi iste migracije.

Konfiguracija:
- Prod: `src/main/resources/application.yml`
- Test: `src/test/resources/application.yml`
