# Task: see this month's skip and undo it

## Goal

A caller should be able to ask whether a habit's monthly skip has been used, and to take back a
skip that was recorded by mistake, so the month's skip is available again.

## Current behavior

`POST /habits/{id}/skip` (`HabitController.java:218`) records one skip for today through
`HabitSkipService.skip`. It is the only skip endpoint: nothing reads a skip back and nothing
removes one.

- Storage: table `habit_skips` (`V17_1__create_habit_skips_table.sql`), one row per skip, with
  `UNIQUE (habit_id, month_start)` and `ON DELETE CASCADE` from `habits`.
- A second skip in the same calendar month is rejected with 409 (`HabitSkipAlreadyUsedException`,
  handled in `GlobalExceptionHandler`).
- `Habit.validateSkipOn` rejects archived habits, days the habit is not scheduled for, and days
  already completed.
- Skips already feed the streak: `HabitQueryService` passes `skippedDates` into the streak
  calculation, and the due-today and dashboard predicates exclude skipped habits.
- `HabitSkipRepository` already has the read shapes needed here (`findByHabitIdAndMonthStart`,
  `findByHabitIdAndSkippedOnBetween`). There is no single-row delete except `deleteByHabitId`,
  which removes every skip of a habit.

## Gap

- A skip cannot be inspected. The caller learns that the month is used only by trying to skip
  again and getting a 409.
- A skip cannot be undone. A skip pressed by mistake burns the month's only skip, and the streak
  keeps treating that day as forgiven even if the habit is completed later the same day.

## Open design decisions (resolve before writing the first test)

1. **What does the read return?**
   - The current month's skip for one habit only (`GET /habits/{id}/skip`, 404 when unused).
   - A list of skips over a range, following how `/history` is shaped.
   - A flag on an existing response (for example `skipUsedThisMonth` on `HabitResponse`), with no
     new endpoint.
2. **Which skip can be undone?**
   - Only today's skip.
   - Any skip in the current calendar month.
   - Any skip at all, including past months.
3. **What happens to the streak when a skip is removed?**
   - Nothing special: the streak is recomputed from the remaining data on the next read.
   - The removal is refused when it would break a streak that currently depends on it.
   Look at `decrementCompletionCount` and `isStreakAliveGiven` before choosing, because a
   duplicated streak formula was the cause of the last bug in this area.
4. **Undo when the day is already completed.** If the habit was completed on the skip day after
   the skip (validation only blocks the reverse order), is removing the skip allowed, a no-op, or
   an error?
5. **Response for a missing skip on DELETE.** 404 (nothing to remove) or 204 (idempotent).
   `uncomplete` in this codebase is the closest existing precedent to check.
6. **Events.** `skip` publishes `DashboardChangedEvent` so the dashboard cache is invalidated. A
   removal changes the same predicates, so it needs the same treatment; decide whether that is
   covered by a test of its own.

## Starting point

Once 1-6 are decided, the first test is a `HabitSkipServiceTest` case for the removal path
(service level, mirroring the existing `skip` tests), before any controller or repository work.
Controller and integration tests follow the same order as the `skip` feature: service, then
`HabitControllerTest`, then `HabitControllerIntegrationTest`.

Docs owed at the end (Claude's part): `docs/api-reference.md` (new endpoint numbers and status
codes, and the `currentStreak` notes that mention skips), `curls.md` (Skip section), and a short
decision trail appended to this file.

## Decisions taken

1. **Read:** `GET /habits/{id}/skip` returns the current calendar month's skip only, 404 when unused.
2. **Undo scope:** `DELETE /habits/{id}/skip` removes only the current calendar month's skip and
   returns 204. Skips from earlier months are history and yield 404.
3. **Streak effect:** removal is refused with 409 if it would change either `currentStreak` or
   `longestStreak`. `longestStreak` was added after review: `decrementCompletionCount` recomputes it,
   so leaving it stale here would let the stored value drift away from what completions and skips
   can derive. The check uses one shared calculation, `Habit.calculateStreaks`, also used by
   `decrementCompletionCount`; the earlier duplicated reconstruction loop was removed.
4. **Skip day later completed:** the skip is redundant, neither value changes, removal is allowed.
5. **Repeated DELETE:** 404, not idempotent 204.
6. **Events:** successful removal publishes `DashboardChangedEvent`.
7. **Scoping:** both endpoints do the owner-scoped habit lookup first, so another client's habit is 404.

Verified by an end-to-end test (POST, GET, DELETE, GET 404, POST again) and by two mutations of
`currentStreakDependsOnSkip` (always `false`, always `true`), each turning the matching tests red.

