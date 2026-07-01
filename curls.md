# habit-tracker — curl cheat sheet

Local server at `http://localhost:8080`. Append `| jq` for formatted JSON.

## Habits CRUD

```bash
# Create (returns the habit with an id — keep it for the rest)
curl -s -X POST http://localhost:8080/habits \
  -H "Content-Type: application/json" \
  -d '{"name": "Read 30 min"}'

# List (paginated — the array is under $.content)
curl -s "http://localhost:8080/habits?page=0&size=10"

# List filtered by name (substring, case-insensitive; combines with includeArchived)
curl -s "http://localhost:8080/habits?name=read&includeArchived=true"

# Get one by id
curl -s http://localhost:8080/habits/1

# Update (needs both version and name — optimistic locking)
curl -s -X PUT http://localhost:8080/habits/1 \
  -H "Content-Type: application/json" \
  -d '{"version": 0, "name": "Read 45 min"}'

# Delete
curl -s -X DELETE http://localhost:8080/habits/1
```

## Completion

```bash
# Complete (emits HabitCompletedEvent to the Kafka topic habit-completed)
# A second complete on the same day is a no-op (idempotent domain) — no event emitted
curl -s -X POST http://localhost:8080/habits/1/complete

# Uncomplete
curl -s -X POST http://localhost:8080/habits/1/uncomplete
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
