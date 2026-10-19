# Task: streak history by habit

## Endpoint

`GET /habits/{id}/streak-history?from=YYYY-MM-DD&to=YYYY-MM-DD`

Returns the habit's streak series that intersect the requested date window. Each item contains
`start`, `end`, `length`, and `current`.

## Contract decisions

1. The response is a chronological JSON array. A series is returned whole, not clipped to the
   requested window; `length` therefore remains the number of actual completion dates in that
   series.
2. Window intersection is inclusive: a series is included exactly when
   `series.start <= effectiveTo && series.end >= effectiveFrom`. A series ending on `from` or
   starting on `to` is included; one ending before `from` or starting after `to` is excluded.
3. Validate the raw range first: `from > to` returns `400`. Then clamp `from` to the habit's
   creation date and `to` to today in the application's business-clock zone. If the effective
   start is after the effective end, return `[]` without reading completion or skip history.
4. A missing habit and a habit owned by another client both return `404`, using the same
   owner-scoped lookup as the other habit endpoints. Archived habits remain readable.
5. Completion dates come from the authoritative `habit_completions` write-side table, not the
   eventually consistent Kafka-fed stats read model. This keeps the returned history current with
   successful completion writes; `/stats` may briefly differ while its read model catches up.
6. Streak continuity uses the domain's existing `isStreakAliveGiven` / `calculateStreakSeries`
   rule and the habit's current schedule. Skip dates can connect completions but do not increase
   `length`. A completion on an unscheduled day is handled by that same existing domain rule; no
   separate interpretation is introduced here.
7. `length` counts unique completion days. The completion table's uniqueness constraint enforces
   at most one completion per habit per day.
8. This is a read-only endpoint: it does not publish `DashboardChangedEvent` and does not invalidate
   dashboard caches.

## Boundary-test scope

The repository query reads the complete completion history between the habit's creation date and
today, inclusive. The requested-window intersection is applied afterward in Java. The integration
test against the database verifies inclusion of completions on the creation-date and today
boundaries; service tests verify both inclusive window intersections and exclusion of series wholly
outside the requested window.

## Known limitations

- The full completion and skip history is loaded for every request; there is no pagination or
  history cutoff.
- Historical schedule changes are not stored for this calculation. Series continuity is evaluated
  with the current scheduled days.
- `/stats` and this endpoint use different data sources (the asynchronous stats projection versus
  the authoritative completion table), so their current-streak values may briefly differ after a
  completion.
