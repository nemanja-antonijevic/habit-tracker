# Task: allow one streak-preserving skip per month

## Goal

A habit's streak should survive one missed scheduled day per month, without the user having to
complete the habit on that day.

## Current behavior

`Habit.isStreakAliveGiven(lastCompletedOn, today)` (`src/main/java/com/nantonijevic/habits/domain/Habit.java:282`)
keeps the streak alive only if the last completion was today, or on the previous scheduled day
before today (`previousScheduledDateBefore`). Any gap beyond that resets the streak. There is
currently no concept of a forgiven miss.

Consumers of this method: `HabitQueryService.currentStreak` (`HabitQueryService.java:396`), used by
both the per-habit stats projection and the dashboard aggregate.

## Open design decisions (resolve before writing the first test)

1. **Explicit action or automatic forgiveness?**
   - Explicit: a `POST /habits/{id}/skip`-style action the caller invokes.
   - Automatic: the first missed scheduled day in a month is silently forgiven with no action.
2. **Where does "skip used" live?**
   - A field on `habits` (e.g. `last_skip_used_on`), one skip in flight at a time.
   - A separate table/row per skip, if history/audit of skip usage matters.
3. **Does a skipped day count toward `completion_count` / history?**
   - Counted as a completion (inflates `completion_count`, indistinguishable from a real one).
   - Tracked separately (e.g. a `SKIPPED` marker) so `wasCompletedOn` and the dashboard can tell
     a skip apart from an actual completion.
4. **What defines "a month"?**
   - Calendar month, resetting on the 1st regardless of when the last skip was used.
   - Rolling 30 days from the last skip usage.

## Starting point

Once 1–4 are decided, the first test targets `Habit.isStreakAliveGiven` (or its replacement with an
added skip parameter) in `HabitTest`. Existing tests around `previousScheduledDateBefore` and
`isStreakAliveGiven` are the ones most likely to need new cases alongside it.
