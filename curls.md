# habit-tracker — curl cheat sheet

Local server at `http://localhost:8080`. Append `| jq` for formatted JSON.

## Client tiers (`X-Api-Key`)

Every response containing a habit is filtered by client tier. The tier comes from the optional
`X-Api-Key` header, resolved through the `api_clients` table. A missing header represents an
anonymous `PUBLIC` client, while a supplied but unknown key is rejected with `401 Unauthorized`.

`PUBLIC` omits `scheduledDays`, `archived` and `createdAt` entirely — the fields are absent from
the JSON, not `null`. `TRUSTED` omits only `createdAt`. `INTERNAL` returns everything.
See [docs/api-reference.md](docs/api-reference.md) for the full matrix.

The header is a response-visibility credential, not a complete authentication system. Keys are
stored in plaintext and no key is seeded by Flyway, so a fresh database supports anonymous
`PUBLIC` requests but rejects every supplied key until it is provisioned. Provision one by hand
to see the other tiers:

```bash
# Against the compose MySQL (user/password/database are all `habits`)
docker compose exec -T mysql \
  mysql -uhabits -phabits habits -e \
  "INSERT INTO api_clients (api_key, tier, name, created_at)
   VALUES ('local-internal-key', 'INTERNAL', 'Local dev', NOW(6));"
```

```bash
# No header → PUBLIC: no scheduledDays, no archived, no createdAt
curl -s http://localhost:8080/habits/1

# INTERNAL → all fields present
curl -s http://localhost:8080/habits/1 \
  -H "X-Api-Key: local-internal-key"

# Unknown supplied key → 401 Unauthorized
curl -s -i http://localhost:8080/habits/1 \
  -H "X-Api-Key: not-a-real-key"

# Filtering applies inside pages too; pagination metadata is unaffected by the tier
curl -s "http://localhost:8080/habits?size=10" \
  -H "X-Api-Key: local-internal-key"
```

The header works on every endpoint below that returns a habit: create, list, get by id, update,
complete, uncomplete, archive, unarchive and due-today. It is omitted from the examples that follow,
so those show `PUBLIC` output.

Successful key-to-tier lookups are cached for 60 seconds. Missing and unknown keys are not cached.
A changed or revoked known key can therefore retain its previous tier for up to 60 seconds.

## Habits CRUD

```bash
# Create (returns the habit with an id — keep it for the rest)
# No scheduledDays → defaults to all 7 days (daily habit)
curl -s -X POST http://localhost:8080/habits \
  -H "Content-Type: application/json" \
  -d '{"name": "Read 30 min"}'

# Create with an explicit weekly schedule (Mon/Wed/Fri)
curl -s -X POST http://localhost:8080/habits \
  -H "Content-Type: application/json" \
  -d '{"name": "Workout", "scheduledDays": ["MONDAY", "WEDNESDAY", "FRIDAY"]}'

# List (paginated — the array is under $.content)
curl -s "http://localhost:8080/habits?page=0&size=10"

# List filtered by name (substring, case-insensitive; combines with includeArchived)
curl -s "http://localhost:8080/habits?name=read&includeArchived=true"

# Get one by id
curl -s http://localhost:8080/habits/1

# Update — patch-style: omit scheduledDays to keep the current schedule
curl -s -X PUT http://localhost:8080/habits/1 \
  -H "Content-Type: application/json" \
  -d '{"version": 0, "name": "Read 45 min"}'

# Update including the schedule (replaces it; empty array [] is rejected with 400)
curl -s -X PUT http://localhost:8080/habits/1 \
  -H "Content-Type: application/json" \
  -d '{"version": 0, "name": "Read 45 min", "scheduledDays": ["TUESDAY", "THURSDAY"]}'

# Delete
curl -s -X DELETE http://localhost:8080/habits/1
```

## Completion

```bash
# Complete (emits HabitCompletedEvent to the Kafka topic habit-completed)
# A second complete on the same day is a no-op (idempotent domain) — no event emitted
# Concurrent same-day completes converge: the optimistic-lock loser retries once → both get 200
# Rejected with 400 if today is not one of the habit's scheduledDays
curl -s -X POST http://localhost:8080/habits/1/complete

# Uncomplete
curl -s -X POST http://localhost:8080/habits/1/uncomplete

# Bulk complete (best-effort; each id lands in completed/skipped/failed/notFound/conflicted)
# Each id runs in its own transaction with one retry on concurrent conflict;
# conflicted = lost the race after the retry (transient, safe to retry), failed = permanent (archived/off-day).
# Only completed ids emit HabitCompletedEvent. 400 if habitIds is empty or > 100 ids
curl -s -X POST http://localhost:8080/habits/bulk-complete \
  -H 'Content-Type: application/json' \
  -d '{"habitIds": [1, 2, 999]}'
```

## Archive

```bash
curl -s -X POST http://localhost:8080/habits/1/archive
curl -s -X POST http://localhost:8080/habits/1/unarchive
```

## Read models

```bash
# Stats
curl -s http://localhost:8080/habits/1/stats

# History
curl -s http://localhost:8080/habits/1/history

# History filtered by inclusive date range (both bounds optional)
curl -s "http://localhost:8080/habits/1/history?from=2024-01-10&to=2024-01-31"

# Completion rate over a window (both bounds required, inclusive)
# -> { "scheduled": 4, "completed": 3, "rate": 0.7500 }; rate is null when nothing was scheduled
curl -s "http://localhost:8080/habits/1/completion-rate?from=2026-07-01&to=2026-07-31"
```

## Due today

```bash
# Active habits scheduled for today and not yet completed today (paginated — $.content)
# "Today" is server-side; there is no date query parameter
curl -s "http://localhost:8080/habits/due-today"
```

## Dashboard

```bash
# Cross-habit summary over all active habits (archived excluded)
# dueToday, completedToday, activeStreaks, longestActiveStreak, totalHabits
curl -s http://localhost:8080/habits/stats
```
