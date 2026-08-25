# ADR 0002: Separate command and query services

- Status: Accepted
- Date: 2026-08-09

## Context

Habit operations were previously combined in one service even though writes and reads have different dependencies and consistency guarantees. Some reads use the transactional `habits` model, while statistics also use the Kafka-maintained `habit_completion_stats` projection.

## Decision

Separate the application layer into `HabitCommandService` and `HabitQueryService`. This is a CQS-oriented organization, not strict CQS, because commands may return updated `Habit` values.

Treat `habit_completion_stats` as an eventually consistent read model for statistics. The application is not full CQRS because some query methods still combine write-side and projected data.

## Alternatives considered

One service would keep navigation simpler but mix write orchestration, read composition and their dependencies.

A facade over the command and query services was also rejected. It would recreate a single collaboration point, hide which side each caller uses, and invite the eight dependencies back into one place, obscuring the boundary the split was intended to expose.

A full CQRS split would isolate all read and write models, but would add infrastructure and consistency complexity that the current application does not require.

## Consequences

Command and query responsibilities, dependencies and tests are easier to understand independently.

After a successful command, write-side responses may already show the new state while stats, completion-rate or dashboard streak values still show the previous projection. This divergence lasts until Kafka processing succeeds and has no guaranteed upper bound.

The application currently has no reconciliation job, dead-letter path or semantic comparison that detects a permanently stale projection.
