# Task: debug run of `GET /habits/{id}/streak-history`

## Scope

Observe the streak series computation in a running application with a debugger attached, using a
real HTTP request. This task changes no production code. Its output is a `logs/debug-log.md` entry
in the 100-day-plan repository and, where the run contradicts the documented behaviour, findings.

## Why this run exists

Endpoint 22 shipped (`593f247`) and was documented (`ea81687`) without a debugger session. The
example response in `docs/api-reference.md` is described as "observed in a local Debug run"; the
debug log has no matching entry. Whether that wording is accurate is decided by this run (see
"Done when").

## Setup

1. Dependencies up (MySQL, and whatever the local profile needs for Kafka and Redis); application
   started in the IDE under the debugger.
2. A daily habit created through the real `POST /habits` request.
3. Completions that produce a gap and a skip-bridged run, for example with dates relative to
   today: two consecutive days, one missed day, then three days where the middle day is bridged by
   a real skip (`skippedOn`) rather than a completion. The exact dates are the author's choice;
   they must produce at least two series.
4. Request the endpoint twice: once with a window covering all series and once with a window that
   touches only the end of the first series (`from` equal to that series' `end`).

## Breakpoints

| Location | Question to answer |
|---|---|
| `HabitQueryService` call site of `calculateStreakSeries` | What do `completionDates`, `skippedDates` and `today` contain when they arrive? Is anything outside the window already dropped? |
| `Habit.calculateStreakSeries`, branch where a series is closed (`series.add` inside the loop) | Which pair of dates closes the first series, and what does `isStreakAliveGiven` return for it? |
| `Habit.calculateStreakSeries`, `current` computation after the loop | Why is `current` `true` or `false` for the last series given `today`? |
| `HabitQueryService`, window `filter` | Which series does the filter drop for the narrow window, and which boundary comparison keeps the one that touches `from`? |

## Expected observations

1. The service passes the full history (from the habit's creation date to today), not the
   requested window; clipping happens only in the final filter.
2. The skipped date makes `isStreakAliveGiven` return `true` across the bridged day, so the run
   with the skip is one series, not two.
3. The first series closes on the last completion before the unbridged gap, with `current = false`.
4. With the narrow window, the series ending on `from` is returned whole (not clipped) and later
   series are excluded only if they start after `to`.

## Done when

- Each breakpoint was hit and its question is answered in one line.
- Any observation that differs from the list above is recorded as a finding, not fixed in this task.
- A `## Bug N` entry is appended to `logs/debug-log.md` with the Reproduce / Isolate / Hypothesize /
  Verify structure.
- The "observed in a local Debug run" sentence in `docs/api-reference.md` is either confirmed by
  this run or corrected in a separate docs commit; it is not edited silently.

## Out of scope

Behaviour of endpoints 10 and 11, cache behaviour, and any change to `isStreakAliveGiven` or the
series model.
