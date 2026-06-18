# habit-tracker — curl cheat sheet

Lokalni server na `http://localhost:8080`. Dodaj `| jq` za formatiran JSON.

## Habits CRUD

```bash
# Create (vraća habit sa id-em — zapamti ga za ostalo)
curl -s -X POST http://localhost:8080/habits \
  -H "Content-Type: application/json" \
  -d '{"name": "Read 30 min"}'

# Lista (paginirano — odgovor je u $.content)
curl -s "http://localhost:8080/habits?page=0&size=10"

# Jedan po id
curl -s http://localhost:8080/habits/1

# Update (treba i version i name — optimistic locking)
curl -s -X PUT http://localhost:8080/habits/1 \
  -H "Content-Type: application/json" \
  -d '{"version": 0, "name": "Read 45 min"}'

# Delete
curl -s -X DELETE http://localhost:8080/habits/1
```

## Completion

```bash
# Complete (emituje HabitCompletedEvent na Kafka topic habit-completed)
# Napomena: drugi complete istog dana je no-op (idempotentan domen) — event ne izlazi
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
```
