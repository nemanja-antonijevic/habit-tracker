# Habit Tracker — API Reference

Full specification of every HTTP endpoint. This is the source of truth for the API contract — update it with every endpoint change.

- **Base URL (local):** `http://localhost:8080`
- **Content-Type:** `application/json` (requests with a body)
- **Setup and run:** see [README.md](../README.md)
- **Quick copy-paste curl examples:** see [curls.md](../curls.md)

All routes live under `/habits` (`HabitController`).

## Endpoint overview

| # | Method | Path | Purpose | CQRS side |
|---|--------|------|---------|-----------|
| 1 | `POST` | `/habits` | Create a habit | Write |
| 2 | `GET` | `/habits` | List habits (paginated) | Write |
| 3 | `GET` | `/habits/{id}` | Get one habit by id | Write |
| 4 | `PUT` | `/habits/{id}` | Update the name (optimistic lock) | Write |
| 5 | `DELETE` | `/habits/{id}` | Delete a habit | Write |
| 6 | `POST` | `/habits/{id}/complete` | Mark as done today | Write + event |
| 7 | `POST` | `/habits/{id}/uncomplete` | Undo today's completion | Write + event |
| 8 | `POST` | `/habits/{id}/archive` | Archive (soft delete) | Write |
| 9 | `POST` | `/habits/{id}/unarchive` | Restore from archive | Write |
| 10 | `GET` | `/habits/{id}/history` | Days the habit was completed | Write (direct read) |
| 11 | `GET` | `/habits/{id}/stats` | Aggregate statistics | Read model (Kafka projection) |

## Data model

### HabitResponse

Standard habit representation. Returned by endpoints 1, 3, 4, 6, 7, 8, 9.

| Field | Type | Description |
|-------|------|-------------|
| `id` | `long` | Habit identifier |
| `name` | `string` | Name |
| `completionCount` | `int` | Total number of completed days |
| `currentStreak` | `int` | Current run of consecutive days |
| `archived` | `boolean` | Whether the habit is archived (soft delete) |
| `createdAt` | `string` (ISO-8601 instant) | Creation time |

Deliberately not exposed (internal fields): `version` (except as update input), `longestStreak`, `lastCompletedAt`.

### HabitCompletionResponse

One completed day. Returned by endpoint 10 as an array.

| Field | Type | Description |
|-------|------|-------------|
| `completedOn` | `string` (ISO-8601 date) | Date (`YYYY-MM-DD`) |

### HabitStatsResponse

Aggregate statistics. Returned by endpoint 11.

| Field | Type | Description |
|-------|------|-------------|
| `completionCount` | `long` | Total number of completed days |
| `longestStreak` | `int` | Longest streak ever recorded |
| `lastCompletedOn` | `string` (ISO-8601 date) \| `null` | Last completed date |
| `currentStreak` | `int` | Current streak, corrected at read time (see endpoint 11) |

### ErrorResponse

Body of every error.

| Field | Type | Description |
|-------|------|-------------|
| `error` | `string` | Error message |

## Status codes

| Code | When |
|------|------|
| `200 OK` | Success with a body |
| `201 Created` | Habit created (with a `Location` header) |
| `204 No Content` | Success with no body (delete) |
| `400 Bad Request` | Validation failed, malformed JSON, or an illegal state transition (`InvalidHabitStateException`) |
| `404 Not Found` | Habit does not exist (`HabitNotFoundException`) |
| `409 Conflict` | Version mismatch on update (`HabitVersionConflictException`) |

`GlobalExceptionHandler` (`@RestControllerAdvice`) centralizes the exception-to-status mapping.

---

## 1. Create a habit

```
POST /habits
```

Request body:

| Field | Type | Constraints |
|-------|------|-------------|
| `name` | `string` | `@NotBlank`, `@Size(max = 255)` |

```json
{ "name": "Read 30 min" }
```

**Response:** `201 Created`, header `Location: /habits/{id}`, body `HabitResponse`.

```json
{ "id": 42, "name": "Read 30 min", "completionCount": 0, "currentStreak": 0, "archived": false, "createdAt": "2026-06-26T08:30:00Z" }
```

| Status | Condition |
|--------|-----------|
| `201` | Created |
| `400` | `name` empty or longer than 255 / malformed body |

## 2. List habits

```
GET /habits
```

Query parameters:

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `name` | `string` | _(no filter)_ | Filters by name, substring and case-insensitive (`SwIm` matches `Swim`). Empty or whitespace-only means no filter (returns all). |
| `includeArchived` | `boolean` | `false` | `false` = active only; `true` = include archived |
| `page` | `int` | `0` | Page index (zero-based) |
| `size` | `int` | `20` | Page size |
| `sort` | `string` | `createdAt,desc` (with `id,desc` as tie-breaker) | `field,asc\|desc` (e.g. `name,asc`) |

`page`/`size`/`sort` are Spring `Pageable` parameters. Both filters (name and archive) run in a single query (`search`, both conditions optional), so `totalElements` and the page count match the returned set.

**Default order:** when the client sends no `sort`, the list comes back `createdAt` descending (newest habits first), with `id` descending as a tie-breaker for habits created at the same instant. An explicit `?sort=` from the client takes precedence. The contract holds for both branches (`includeArchived=true` and `false`).

**Response:** `200 OK`, Spring `Page<HabitResponse>`.

```json
{
  "content": [ { "id": 42, "name": "Read 30 min", "completionCount": 0, "currentStreak": 0, "archived": false, "createdAt": "2026-06-26T08:30:00Z" } ],
  "totalElements": 1,
  "totalPages": 1,
  "number": 0,
  "size": 20
}
```

By default the list returns active habits only; `?includeArchived=true` includes archived ones. `?name=` filters by name (substring, case-insensitive) and combines with `includeArchived` (e.g. `?name=read&includeArchived=true`).

## 3. Get one habit by id

```
GET /habits/{id}
```

| Path param | Type | Description |
|------------|------|-------------|
| `id` | `long` | Habit identifier |

**Response:** `200 OK`, `HabitResponse`.

| Status | Condition |
|--------|-----------|
| `200` | Found |
| `404` | Does not exist |

## 4. Update a habit

```
PUT /habits/{id}
```

Request body:

| Field | Type | Constraints |
|-------|------|-------------|
| `version` | `long` | `@NotNull` — current habit version (optimistic locking) |
| `name` | `string` | `@NotBlank`, `@Size(max = 255)` — new name |

```json
{ "version": 3, "name": "Read 45 min" }
```

**Response:** `200 OK`, `HabitResponse` (with `version` incremented in the database).

| Status | Condition |
|--------|-----------|
| `200` | Updated |
| `400` | Validation failed / malformed body |
| `404` | Does not exist |
| `409` | Request `version` does not match the database version (someone updated it in the meantime) |

## 5. Delete a habit

```
DELETE /habits/{id}
```

| Path param | Type | Description |
|------------|------|-------------|
| `id` | `long` | Habit identifier |

**Response:** `204 No Content`, no body.

| Status | Condition |
|--------|-----------|
| `204` | Deleted |
| `404` | Does not exist (including a repeated `DELETE` of an already-deleted habit) |

## 6. Mark as done

```
POST /habits/{id}/complete
```

No body. The server takes the date (`LocalDate.now()`). Increments `completionCount`, updates `currentStreak`/`longestStreak`, writes a history row, and emits `HabitCompletedEvent` to the Kafka topic `habit-completed`.

A repeated `complete` on the same day is a no-op — no duplicate row, no new event, streak unchanged.

**Response:** `200 OK`, `HabitResponse`.

| Status | Condition |
|--------|-----------|
| `200` | Marked (or a no-op if already marked today) |
| `400` | Habit is archived |
| `404` | Does not exist |

## 7. Undo today's completion

```
POST /habits/{id}/uncomplete
```

No body. Deletes today's history row, reverts `completionCount`/`currentStreak`/`lastCompletedAt` to the previous state, and emits `HabitUncompletedEvent`.

**Response:** `200 OK`, `HabitResponse`.

| Status | Condition |
|--------|-----------|
| `200` | Undone |
| `400` | Habit was not completed today, or is archived |
| `404` | Does not exist |

## 8. Archive a habit

```
POST /habits/{id}/archive
```

No body. Sets `archived = true` (soft delete — the habit and its history stay in the database). An archived habit cannot be completed or uncompleted (returns `400`).

Idempotent — a repeated `archive` is a no-op.

**Response:** `200 OK`, `HabitResponse`.

| Status | Condition |
|--------|-----------|
| `200` | Archived |
| `404` | Does not exist |

## 9. Restore from archive

```
POST /habits/{id}/unarchive
```

No body. Sets `archived = false`.

Idempotent — a repeated `unarchive` is a no-op.

**Response:** `200 OK`, `HabitResponse`.

| Status | Condition |
|--------|-----------|
| `200` | Restored from archive |
| `404` | Does not exist |

## 10. Completion history

```
GET /habits/{id}/history
```

| Path param | Type | Description |
|------------|------|-------------|
| `id` | `long` | Habit identifier |

Query parameters:

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `page` | `int` | `0` | Page index (zero-based) |
| `size` | `int` | `20` | Page size |
| `from` | `LocalDate` (`YYYY-MM-DD`) | _none_ | Only completions on or after this date. Omitted means unbounded below. |
| `to` | `LocalDate` (`YYYY-MM-DD`) | _none_ | Only completions on or before this date. Omitted means unbounded above. |

Read directly from the write-side table `habit_completions` (always accurate, synchronous). The order is fixed `completedOn` descending (most recent day first) — the client's `?sort=` does not apply, since history has one meaningful order.

Both `from` and `to` are optional and inclusive. Supplying only one bounds that side and leaves the other open; supplying neither returns the full history. When both are given, `from` must not be after `to`.

**Response:** `200 OK`, Spring `Page<HabitCompletionResponse>`.

```json
{
  "content": [
    { "completedOn": "2026-06-25" },
    { "completedOn": "2026-06-24" }
  ],
  "totalElements": 2,
  "totalPages": 1,
  "number": 0,
  "size": 20
}
```

| Status | Condition |
|--------|-----------|
| `200` | OK (empty `content: []` when there are no completed days, or none fall in the requested range) |
| `400` | `from` is after `to` |
| `404` | Does not exist |

## 11. Aggregate statistics

```
GET /habits/{id}/stats
```

| Path param | Type | Description |
|------------|------|-------------|
| `id` | `long` | Habit identifier |

Read from the read model `habit_completion_stats` (Kafka projection).

**Response:** `200 OK`, `HabitStatsResponse`.

```json
{ "completionCount": 7, "longestStreak": 5, "lastCompletedOn": "2026-06-25", "currentStreak": 4 }
```

| Status | Condition |
|--------|-----------|
| `200` | OK (zeros / `null` when there is no data) |
| `404` | Does not exist |

Behavior:
- **Eventual consistency** — the read model is filled asynchronously over Kafka. A call right after `complete` may return the old state until the consumer processes the event.
- **`currentStreak` is corrected at read time** — the read model stores the streak from the last completion. If the last completion was today or yesterday, it returns the stored value; otherwise `0` (the streak expired before an event arrived to reset it).

---

## Known limitations

None open at the API-contract level. (Items are added here when discovered and removed when resolved.)
