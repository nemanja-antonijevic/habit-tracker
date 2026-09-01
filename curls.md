# habit-tracker — curl cheat sheet

Local server at `http://localhost:8080`. Append `| jq` for formatted JSON.

## Authentication (`X-Api-Key`) — required

**`X-Api-Key` is mandatory on every `/habits` call.** A missing, unknown or revoked key gets
`401 Unauthorized` before the controller runs, so all examples below include the header. Anonymous
requests no longer work.

Once authenticated, the client's tier filters habit responses. `PUBLIC` omits `scheduledDays`,
`archived` and `createdAt` entirely — the fields are absent from the JSON, not `null`. `TRUSTED`
omits only `createdAt`. `INTERNAL` returns everything.
See [docs/api-reference.md](docs/api-reference.md) for the full matrix.

Only a lowercase SHA-256 hash of each key is stored in `api_clients`; the raw key is sent only in the
request header. SHA-256 is deterministic so the high-entropy key can use a unique indexed lookup.
No key is seeded by Flyway, so on a fresh database **every** `/habits` request returns `401` until a
hash is provisioned. Provision one by hand before anything below will work:

```bash
# Against the compose MySQL (user/password/database are all `habits`)
docker compose exec -T mysql \
  mysql -uhabits -phabits habits -e \
  "INSERT INTO api_clients (
    api_key_hash,
    tier,
    name,
    created_at
)
VALUES (
    SHA2('local-internal-key', 256),
    'INTERNAL',
    'Local dev',
    NOW(6)
);"
```

```bash
# INTERNAL → all fields present
curl -s http://localhost:8080/habits/1 \
  -H "X-Api-Key: local-internal-key"

# No header → 401 Unauthorized (was PUBLIC before ADR 0005 step 3)
curl -s -i http://localhost:8080/habits/1

# Unknown key → 401 Unauthorized
curl -s -i http://localhost:8080/habits/1 \
  -H "X-Api-Key: not-a-real-key"

# Revoked client (active = FALSE) → 401 Unauthorized
docker compose exec -T mysql mysql -uhabits -phabits habits -e \
  "UPDATE api_clients SET active = FALSE WHERE name = 'Local dev';"
curl -s -i http://localhost:8080/habits/1 \
  -H "X-Api-Key: local-internal-key"
docker compose exec -T mysql mysql -uhabits -phabits habits -e \
  "UPDATE api_clients SET active = TRUE WHERE name = 'Local dev';"

# Invalid body with an unknown key → 401, not 400: auth runs before validation
curl -s -i -X POST http://localhost:8080/habits \
  -H "Content-Type: application/json" \
  -H "X-Api-Key: not-a-real-key" \
  -d '{"name": ""}'

# Filtering applies inside pages too; pagination metadata is unaffected by the tier
curl -s "http://localhost:8080/habits?size=10" \
  -H "X-Api-Key: local-internal-key"
```

To save repetition in the examples below, export the key once:

```bash
export KEY="X-Api-Key: local-internal-key"
```

Identity is resolved from the database on every request — nothing is cached, so revoking a key or
changing a tier takes effect on the next call.

## Habits CRUD

```bash
# Create (returns the habit with an id — keep it for the rest)
# No scheduledDays → defaults to all 7 days (daily habit)
curl -s -X POST http://localhost:8080/habits \
  -H "Content-Type: application/json" -H "$KEY" \
  -d '{"name": "Read 30 min"}'

# Create with an explicit weekly schedule (Mon/Wed/Fri)
curl -s -X POST http://localhost:8080/habits \
  -H "Content-Type: application/json" -H "$KEY" \
  -d '{"name": "Workout", "scheduledDays": ["MONDAY", "WEDNESDAY", "FRIDAY"]}'

# List (paginated — the array is under $.content)
curl -s "http://localhost:8080/habits?page=0&size=10" -H "$KEY"

# List filtered by name (substring, case-insensitive; combines with includeArchived)
curl -s "http://localhost:8080/habits?name=read&includeArchived=true" -H "$KEY"

# Get one by id
curl -s http://localhost:8080/habits/1 -H "$KEY"

# Update — patch-style: omit scheduledDays to keep the current schedule
curl -s -X PUT http://localhost:8080/habits/1 \
  -H "Content-Type: application/json" -H "$KEY" \
  -d '{"version": 0, "name": "Read 45 min"}'

# Update including the schedule (replaces it; empty array [] is rejected with 400)
curl -s -X PUT http://localhost:8080/habits/1 \
  -H "Content-Type: application/json" -H "$KEY" \
  -d '{"version": 0, "name": "Read 45 min", "scheduledDays": ["TUESDAY", "THURSDAY"]}'

# Delete
curl -s -X DELETE http://localhost:8080/habits/1 -H "$KEY"
```

## Completion

```bash
# Complete (emits HabitCompletedEvent to the Kafka topic habit-completed)
# A second complete on the same day is a no-op (idempotent domain) — no event emitted
# Concurrent same-day completes converge: the optimistic-lock loser retries once → both get 200
# Rejected with 400 if today is not one of the habit's scheduledDays
curl -s -X POST http://localhost:8080/habits/1/complete -H "$KEY"

# Uncomplete
curl -s -X POST http://localhost:8080/habits/1/uncomplete -H "$KEY"

# Bulk complete (best-effort; each id lands in completed/skipped/failed/notFound/conflicted)
# Each id runs in its own transaction with one retry on concurrent conflict;
# conflicted = lost the race after the retry (transient, safe to retry), failed = permanent (archived/off-day).
# Only completed ids emit HabitCompletedEvent. 400 if habitIds is empty or > 100 ids
curl -s -X POST http://localhost:8080/habits/bulk-complete \
  -H 'Content-Type: application/json' -H "$KEY" \
  -d '{"habitIds": [1, 2, 999]}'
```

## Archive

```bash
curl -s -X POST http://localhost:8080/habits/1/archive -H "$KEY"
curl -s -X POST http://localhost:8080/habits/1/unarchive -H "$KEY"
```

## Read models

```bash
# Stats
curl -s http://localhost:8080/habits/1/stats -H "$KEY"

# History
curl -s http://localhost:8080/habits/1/history -H "$KEY"

# History filtered by inclusive date range (both bounds optional)
curl -s "http://localhost:8080/habits/1/history?from=2024-01-10&to=2024-01-31" -H "$KEY"

# Completion rate over a window (both bounds required, inclusive)
# -> { "scheduled": 4, "completed": 3, "rate": 0.7500 }; rate is null when nothing was scheduled
curl -s "http://localhost:8080/habits/1/completion-rate?from=2026-07-01&to=2026-07-31" -H "$KEY"
```

## Due today

```bash
# Active habits scheduled for today and not yet completed today (paginated — $.content)
# "Today" is server-side; there is no date query parameter
curl -s "http://localhost:8080/habits/due-today" -H "$KEY"
```

## Dashboard

```bash
# Cross-habit summary over all active habits (archived excluded)
# dueToday, completedToday, activeStreaks, longestActiveStreak, totalHabits
curl -s http://localhost:8080/habits/stats -H "$KEY"
```
