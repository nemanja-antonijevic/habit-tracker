# Habit Tracker

Event-driven habit tracking app. Side project for my 100-day BE plan.

## Stack (po fazama)

- **F1 (d1-21):** Spring Boot 3, PostgreSQL, Flyway, Testcontainers
- **F2 (d22-50):** + Kafka producer/consumer, async stats, idempotentnost
- **F3 (d51-78):** + Redis, concurrency challenges, JFR profiling
- **F4 (d79-100):** + Docker Compose, GitHub Actions CI, Prometheus

## Local dev

docker compose up -d   # Postgres (Kafka u F2)                                                                                                                                                                                                 
./mvnw spring-boot:run

## Tests

./mvnw test            # Testcontainers spawnuje Postgres automatski