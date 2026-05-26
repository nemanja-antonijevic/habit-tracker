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

Habits CRUD:

```bash
# Create
curl -X POST http://localhost:8080/habits \
  -H "Content-Type: application/json" \
  -d '{"name":"Code 3 hours","description":"Daily Java practice"}'

# List
curl http://localhost:8080/habits

# Get by id
curl http://localhost:8080/habits/1

# Update
curl -X PUT http://localhost:8080/habits/1 \
  -H "Content-Type: application/json" \
  -d '{"name":"Code 4 hours","description":"Updated"}'

# Delete
curl -X DELETE http://localhost:8080/habits/1
```

## Project layout

```
src/main/java/com/nantonijevic/habittracker/
  HabitTrackerApplication.java   # Spring Boot entry
  habit/
    Habit.java                   # @Entity
    HabitRepository.java         # Spring Data JPA
    HabitController.java         # REST endpoints
    dto/                         # request/response records
src/main/resources/
  application.yml                # MySQL datasource (prod)
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
