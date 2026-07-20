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
| 4 | `PUT` | `/habits/{id}` | Update the name and/or schedule (optimistic lock) | Write |
| 5 | `DELETE` | `/habits/{id}` | Delete a habit | Write |
| 6 | `POST` | `/habits/{id}/complete` | Mark as done today | Write + event |
| 7 | `POST` | `/habits/{id}/uncomplete` | Undo today's completion | Write + event |
| 8 | `POST` | `/habits/{id}/archive` | Archive (soft delete) | Write |
| 9 | `POST` | `/habits/{id}/unarchive` | Restore from archive | Write |
| 10 | `GET` | `/habits/{id}/history` | Days the habit was completed | Write (direct read) |
| 11 | `GET` | `/habits/{id}/stats` | Aggregate statistics | Read model (Kafka projection) |
| 12 | `GET` | `/habits/due-today` | Active habits scheduled for today and not yet completed | Write (direct read) |
| 13 | `POST` | `/habits/bulk-complete` | Mark many habits as done today (best-effort, per-item result) | Write + event |
| 14 | `GET` | `/habits/due-today/count` | Number of habits due today | Write (direct read) |
| 15 | `GET` | `/habits/stats` | Cross-habit dashboard summary over all active habits | Write + read model (Kafka projection) |
| 16 | `GET` | `/habits/{id}/completion-rate` | Completion rate over a date window | Read model (Kafka projection) |

## Data model

### HabitResponse

Standard habit representation. Returned by endpoints 1, 3, 4, 6, 7, 8, 9.

| Field | Type | Description |
|-------|------|-------------|
| `id` | `long` | Habit identifier |
| `name` | `string` | Name |
| `scheduledDays` | `string[]` | Days of the week the habit is due, as `DayOfWeek` names (`"MONDAY"` … `"SUNDAY"`). A habit with all 7 days behaves like a daily habit. |
| `completionCount` | `int` | Total number of completed days |
| `currentStreak` | `int` | Current run of consecutive scheduled days, corrected at read time: the stored streak if the last completion was today or the previous scheduled day, otherwise `0` |
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
| `currentStreak` | `int` | Current streak, corrected at read time against the schedule (see endpoint 11) |

### HabitCompletionRateResponse

Completion rate over a date window. Returned by endpoint 16.

| Field | Type | Description |
|-------|------|-------------|
| `scheduled` | `long` | Number of scheduled occurrences in the effective window |
| `completed` | `long` | Of those, how many were actually completed |
| `rate` | `number` (0..1, scale 4) \| `null` | `completed / scheduled`, rounded `HALF_UP` to 4 decimals; `null` when `scheduled` is `0` (rate is undefined, not `0`) |

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
| `scheduledDays` | `string[]` | Optional. `DayOfWeek` names (`"MONDAY"` … `"SUNDAY"`). `@Size(min = 1)`: omit the field entirely to default to all 7 days (daily), but an empty array `[]` is rejected. |

Minimal (daily habit — no schedule sent):

```json
{ "name": "Read 30 min" }
```

With an explicit schedule (Mon/Wed/Fri):

```json
{ "name": "Workout", "scheduledDays": ["MONDAY", "WEDNESDAY", "FRIDAY"] }
```

**Response:** `201 Created`, header `Location: /habits/{id}`, body `HabitResponse`.

```json
{ "id": 42, "name": "Read 30 min", "scheduledDays": ["MONDAY","TUESDAY","WEDNESDAY","THURSDAY","FRIDAY","SATURDAY","SUNDAY"], "completionCount": 0, "currentStreak": 0, "archived": false, "createdAt": "2026-06-26T08:30:00Z" }
```

| Status | Condition |
|--------|-----------|
| `201` | Created |
| `400` | `name` empty or longer than 255 / `scheduledDays` is an empty array / malformed body |

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
  "content": [ { "id": 42, "name": "Read 30 min", "scheduledDays": ["MONDAY","TUESDAY","WEDNESDAY","THURSDAY","FRIDAY","SATURDAY","SUNDAY"], "completionCount": 0, "currentStreak": 0, "archived": false, "createdAt": "2026-06-26T08:30:00Z" } ],
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
| `scheduledDays` | `string[]` | Optional. `DayOfWeek` names. `@Size(min = 1)`: **patch-style** — omit the field to leave the existing schedule unchanged; send an array to replace it. An empty array `[]` is rejected. |

Rename only (schedule left untouched):

```json
{ "version": 3, "name": "Read 45 min" }
```

Change the schedule too:

```json
{ "version": 3, "name": "Read 45 min", "scheduledDays": ["TUESDAY", "THURSDAY"] }
```

**Response:** `200 OK`, `HabitResponse` (with `version` incremented in the database).

| Status | Condition |
|--------|-----------|
| `200` | Updated |
| `400` | Validation failed / `scheduledDays` is an empty array / malformed body |
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

**The habit can only be completed on a scheduled day.** If today is not in the habit's `scheduledDays`, the call is rejected with `400`. The streak counts consecutive *scheduled* days: completing on Friday and then Monday keeps the streak alive for a Mon/Wed/Fri habit, because Saturday and Sunday are not scheduled.

**Response:** `200 OK`, `HabitResponse`.

| Status | Condition |
|--------|-----------|
| `200` | Marked (or a no-op if already marked today) |
| `400` | Habit is archived, or today is not a scheduled day |
| `404` | Does not exist |

## 7. Undo today's completion

```
POST /habits/{id}/uncomplete
```

No body. Deletes today's history row, then recomputes `completionCount`, `currentStreak`, and `longestStreak` from the remaining completion history (walking consecutive scheduled days, so a gap breaks the run), and emits `HabitUncompletedEvent`. `currentStreak` is gated to the live window — if the latest remaining completion is neither today nor the previous scheduled day it becomes `0`, while `longestStreak` still reflects the best past run.

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
- **`currentStreak` is corrected at read time against the schedule** — the read model stores the streak from the last completion. If the last completion was today or the previous scheduled day, it returns the stored value; otherwise `0` (the streak expired before an event arrived to reset it). For a daily habit (all 7 days) the previous scheduled day is simply yesterday.

## 12. Habits due today

```
GET /habits/due-today
```

Returns the active habits that are **due today**: scheduled for today's day of week and not yet completed today. "Today" is resolved server-side from the system clock — there is no date query parameter.

Query parameters:

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `page` | `int` | `0` | Page index (zero-based) |
| `size` | `int` | `20` | Page size |
| `sort` | `string` | _(unspecified)_ | `field,asc\|desc` Spring `Pageable` sort |

A habit is included only when **all three** hold:
- it is active (archived habits are excluded);
- today's `DayOfWeek` is in its `scheduledDays`;
- it has not been completed today.

Filtering runs in memory over the active habits (the schedule is a converted column, not queryable in SQL), then the result is paginated. This is fine at personal scale; it is not intended for large data sets.

**Response:** `200 OK`, Spring `Page<HabitResponse>`.

```json
{
  "content": [ { "id": 42, "name": "Workout", "scheduledDays": ["MONDAY","WEDNESDAY","FRIDAY"], "completionCount": 3, "currentStreak": 3, "archived": false, "createdAt": "2026-06-26T08:30:00Z" } ],
  "totalElements": 1,
  "totalPages": 1,
  "number": 0,
  "size": 20
}
```

| Status | Condition |
|--------|-----------|
| `200` | OK (empty `content: []` when nothing is due today) |

## 13. Bulk complete

```
POST /habits/bulk-complete
```

Mark many habits as done today in one call. "Today" is resolved server-side from the system clock — there is no date in the request.

**Why this exists alongside [endpoint 6](#6-mark-as-done).** The two are not redundant — they are two different *contracts* over the same operation, the same way Spring Data exposes both `save` and `saveAll`. Endpoint 6 is resource-scoped and fail-fast: it targets one habit and reports the outcome through the HTTP status (`404` for a missing habit, `400` for archived / off-day), which is the right ergonomics for the common single-habit case. Bulk-complete is best-effort: it always returns `200` and reports each id's outcome in the body, so one bad id never rolls back the others — the right shape for a "close out the day" action over several habits. The shared domain logic is not duplicated: both paths funnel through the same `completeExistingHabit` method in `HabitService`, so only the contract (error model, response shape, target cardinality) differs.

Request body (`BulkCompleteRequest`):

```json
{ "habitIds": [1, 2, 3] }
```

| Field | Type | Constraints |
|-------|------|-------------|
| `habitIds` | `long[]` | Required, non-empty, at most 100 ids |

**Best-effort semantics.** Each id is processed independently — one bad id never rolls back the others. The call returns `200 OK` with a per-item breakdown; the outcome is in the body, not the status code. For each id, exactly one bucket applies (checked in this order):

- `notFound` — no habit with that id;
- `failed` — the habit exists but cannot be completed today (archived, or today is not a scheduled day);
- `skipped` — already completed today (idempotent no-op, not an error);
- `completed` — newly marked done today.

Each newly `completed` habit follows the same path as [endpoint 6](#6-mark-as-done): it writes a history row and emits `HabitCompletedEvent`. `skipped`/`failed`/`notFound` produce no write and no event.

A duplicate id within the same request (e.g. `[1, 1]`) completes on the first occurrence and is `skipped` on the second, so the same id can appear in both `completed` and `skipped`.

**Response:** `200 OK`, `BulkCompleteResponse`.

```json
{ "completed": [1], "skipped": [2], "failed": [3], "notFound": [999] }
```

| Field | Type | Description |
|-------|------|-------------|
| `completed` | `long[]` | Newly marked done today |
| `skipped` | `long[]` | Already completed today |
| `failed` | `long[]` | Archived, or today is not a scheduled day |
| `notFound` | `long[]` | No habit with that id |

| Status | Condition |
|--------|-----------|
| `200` | Processed (see body for the per-item breakdown) |
| `400` | `habitIds` is missing, empty, or has more than 100 ids |

## 14. Count of habits due today

```
GET /habits/due-today/count
```

Returns just the **count** of habits due today, without the habit payloads. "Due today" is defined exactly as for [endpoint 12](#12-habits-due-today) — the same predicate is shared in `HabitService` so the two can never disagree. "Today" is resolved server-side from the system clock; there is no date query parameter and no pagination.

A habit is counted only when **all three** hold:
- it is active (archived habits are excluded);
- today's `DayOfWeek` is in its `scheduledDays`;
- it has not been completed today.

Like endpoint 12, the count is computed in memory over the active habits (the schedule is a converted column, not queryable in SQL). Fine at personal scale; not intended for large data sets.

**Response:** `200 OK`, `DueTodayCountResponse`.

```json
{ "count": 3 }
```

| Field | Type | Description |
|-------|------|-------------|
| `count` | `long` | Number of habits due today |

| Status | Condition |
|--------|-----------|
| `200` | OK (`{ "count": 0 }` when nothing is due today) |

## 15. Cross-habit dashboard stats

```
GET /habits/stats
```

Returns a single aggregate summary across **all active habits** — a dashboard header, not per-habit detail. Archived habits are excluded from every field. "Today" is resolved server-side from the system clock; there is no date query parameter and no pagination.

**Response:** `200 OK`, `HabitDashboardResponse`.

```json
{ "dueToday": 2, "completedToday": 1, "activeStreaks": 1, "longestActiveStreak": 4, "totalHabits": 3 }
```

| Field | Type | Description |
|-------|------|-------------|
| `dueToday` | `long` | Active habits scheduled for today |
| `completedToday` | `long` | Of those due today, how many are already completed |
| `activeStreaks` | `long` | Active habits whose streak is still alive today |
| `longestActiveStreak` | `int` | Largest live streak among active habits; `0` when none is alive |
| `totalHabits` | `long` | Total active habits |

| Status | Condition |
|--------|-----------|
| `200` | OK (all fields `0` when there are no active habits) |

Behavior:
- **Two data sources, by design.** `dueToday` / `completedToday` / `totalHabits` come from the write side (direct read of active habits), so they are immediately consistent. `activeStreaks` / `longestActiveStreak` come from the read model `habit_completion_stats` (Kafka projection), so they are **eventually consistent** — in the short window after a `complete` before the consumer processes the event, a habit can count toward `completedToday` while its streak has not yet landed in the read model.
- **Streak liveness is corrected at read time against the schedule** — same rule as [endpoint 11](#11-habit-stats): a stored streak counts only if the last completion was today or the previous scheduled day, otherwise it is treated as `0`. The rule lives in one place (`Habit.isStreakAliveGiven`) shared by both endpoints so the two can never disagree.
- **No N+1.** The summary is computed with exactly two queries regardless of habit count: one for the active habits, one batch query for their latest completion stats.
- **Cached in Redis (cache-aside), keyed by date.** The response is cached under `dashboard-stats::<today>` with a 5-minute TTL. The cache is invalidated (whole `dashboard-stats` region cleared) *after commit* on any write that can affect the summary — `create`, `update`, `archive`, `unarchive`, `delete`, `complete`, `uncomplete`, `bulkComplete` — and again after the Kafka consumer updates the read model for `complete` / `uncomplete`. Because invalidation fires only after the transaction commits, a concurrent read cannot repopulate the cache with pre-commit state. The TTL is a safety net (missed invalidation, stalled consumer, manual data change), not the primary mechanism; it bounds how long a stale entry can live, but does not by itself close the eventual-consistency window described above.

## 16. Completion rate over a window

```
GET /habits/{id}/completion-rate?from=2026-07-01&to=2026-07-31
```

Returns how consistently a habit was kept over a date window: the ratio of completed scheduled days to total scheduled days. This is a historical consistency metric, not a current snapshot (see [endpoint 11](#11-habit-stats) for the snapshot).

Query parameters:

| Param | Type | Required | Description |
|-------|------|----------|-------------|
| `from` | `LocalDate` (`YYYY-MM-DD`) | yes | Start of the window (inclusive) |
| `to` | `LocalDate` (`YYYY-MM-DD`) | yes | End of the window (inclusive) |

**Response:** `200 OK`, `HabitCompletionRateResponse`.

```json
{ "scheduled": 4, "completed": 3, "rate": 0.7500 }
```

| Status | Condition |
|--------|-----------|
| `200` | OK |
| `400` | `from` after `to`, or a required parameter is missing / unparseable |
| `404` | Habit does not exist |

Behavior:
- **Inclusive window, clamped to the habit's age.** Both `from` and `to` count. The effective start is `max(from, habitCreatedDate)` — scheduled occurrences before the habit existed are never counted (`createdAt` is converted to a date with the system default zone, consistent with the rest of the domain). If the effective start is after `to` the window is empty and the response is `{ "scheduled": 0, "completed": 0, "rate": null }` without touching the read model.
- **Numerator and denominator share the same effective window.** `completed` can therefore never exceed `scheduled`, so `rate` is always in `0..1`. The `UNIQUE(habit_id, completed_on)` constraint (migration V10) guarantees one completion row per day, so there is no double counting.
- **Day-of-week filtering happens in Java, not SQL.** Completed dates are read with a plain `BETWEEN` query, then filtered against the current `scheduledDays` using `LocalDate.getDayOfWeek()`. This deliberately avoids the database `DAYOFWEEK` function, whose weekday numbering differs between H2 and MySQL (and from Java's `DayOfWeek`).
- **`rate` is `null` vs `0`, on purpose.** `scheduled == 0` (nothing was ever scheduled in the window) returns `rate: null` — undefined, not zero. `scheduled > 0` with `completed == 0` returns `rate: 0.0000` — a defined zero rate. The rate is rounded `HALF_UP` to scale 4 (e.g. `1/3` → `0.3333`).
- **Eventual consistency.** Completed dates come from the read model `habit_completion_stats` (Kafka projection), so a very recent `complete` may not yet be reflected.

### Known limitation — schedule history

The rate is computed against the habit's **current** `scheduledDays` for the whole window. There is no schedule-change history, so if the schedule changed within the window the older portion is measured against today's schedule. A completion on a day that is no longer scheduled does not count toward `completed`. Historically accurate rates across schedule changes would require a separate schedule-history model, which is out of scope.
