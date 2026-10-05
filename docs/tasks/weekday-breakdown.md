# Task: completion breakdown by weekday

## Goal

A caller should be able to see, for a habit and a date window, how the scheduled days and the
completions split across the days of the week, so it is visible which weekdays a habit is kept on.

## Current behavior

`GET /habits/{id}/completion-rate` returns one `scheduled` / `completed` pair for the whole window.
It is computed in `HabitQueryService` by counting scheduled occurrences and filtering the completed
dates from `habit_completion_stats` against the current `scheduledDays`.

## Decisions (made before implementation)

1. **Response shape.** A map keyed by `DayOfWeek`, each value with `scheduled` and `completed`.
2. **Which weekdays appear.** Only the days of the habit's current schedule.
3. **Empty weekdays stay.** A scheduled weekday with no occurrence in the effective window is still
   present with zeros, so the shape of the response does not depend on the window.
4. **Window.** Inclusive on both ends; the start is clamped to the habit's creation date in the
   business time zone.
5. **Empty effective window.** If the clamped start is after `to`, every scheduled weekday is
   returned with zeros and the completion read model is not queried.
6. **Completions on unscheduled days.** Not counted, as in `completion-rate`.
7. **Statuses.** Same as `completion-rate`: `400` for a missing or malformed parameter or an
   inverted range, `404` for a missing habit or one owned by another client.
8. **No cache invalidation.** The endpoint is read-only and publishes no `DashboardChangedEvent`.
9. **No second copy of the window logic.** One shared calculation owns the range check, owner
   lookup, creation-date clamp, empty-window short circuit, inclusive counting, completed-date
   loading, schedule filter and per-weekday aggregation. The new endpoint returns its result, and
   `getCompletionRate` sums it. The former `countScheduledOccurrences` was removed.

## Tests

- Service: grouping by weekday with unequal values per day, an off-schedule completion that must
  not appear, and an empty effective window that must not query the read model.
- Controller: forwarding, response shape, `400` and `404` cases, plus the parameterised cross-owner
  `404` check.
- Protection of the shared calculation: replacing the creation-date clamp with `effectiveFrom =
  from` fails the existing `completionRateStartsAtHabitCreationDateWhenHabitIsYoungerThanWindow`,
  so the clamp is covered through both endpoints.

## Known limitation

The window is walked day by day and `to` is not bounded, so a very distant `to` makes the cost
grow with the window length. Future days also count as scheduled. Not changed in this task; see
the Known limitations section of endpoint 20 in `docs/api-reference.md`.
