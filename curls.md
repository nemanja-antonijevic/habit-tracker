# habit-tracker — curl cheat sheet

Local server at `http://localhost:8080`. Append `| jq` for formatted JSON.

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
# Rejected with 400 if today is not one of the habit's scheduledDays
curl -s -X POST http://localhost:8080/habits/1/complete

# Uncomplete
curl -s -X POST http://localhost:8080/habits/1/uncomplete

# Bulk complete (best-effort; each id lands in completed/skipped/failed/notFound)
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
```

## Due today

```bash
# Active habits scheduled for today and not yet completed today (paginated — $.content)
# "Today" is server-side; there is no date query parameter
curl -s "http://localhost:8080/habits/due-today"
```
