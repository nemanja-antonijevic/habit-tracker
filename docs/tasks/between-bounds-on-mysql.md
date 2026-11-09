# Task: prove the `...Between` boundary behaviour on real MySQL

## Scope

Establish, on a real MySQL 8.0 instance, how the three derived `Between` repository methods treat
their boundaries, and observe one of them in a running application under a debugger. This task adds
an integration test and a debug log entry. It changes no production code; a disagreement between
prediction and observation is recorded as a finding, not fixed here.

## Why this task exists

Bug 43 (`GET /habits/{id}/streak-history` Debug run) ran against a temporary H2 database because
Docker was unavailable, and states in its "Ograda" that MySQL-specific behaviour was not proven.
Every existing test of these methods either mocks the repository (`HabitQueryServiceTest`,
`HabitSkipServiceTest`, `HabitCommandServiceTest`) or runs on H2. Endpoint 22 depends on the lower
boundary being inclusive: a series ending exactly on `from` must be returned.

## Methods under test

| Method | Repository | Used by |
|---|---|---|
| `findByHabitIdAndSkippedOnBetween` | `HabitSkipRepository` | `HabitQueryService`, `HabitSkipService`, `HabitCommandService` |
| `findByHabitIdInAndSkippedOnBetween` | `HabitSkipRepository` | `HabitQueryService` (batch) |
| `findByHabitIdAndCompletedOnBetweenOrderByCompletedOnAsc` | `HabitCompletionRepository` | `HabitQueryService` |

Out of scope: `findByHabitIdAndCompletedOnBetweenOptional` (explicit `>=` / `<=` in `@Query`, so the
boundary is written in the query, not derived), cache behaviour, and any change to a repository.

## Step 1: predictions, written before any code

For each method, with rows on `D-1`, `D`, `D+1` (a different habit's rows on the same dates
included as a control for the `HabitIdIn` variant and for the habit filter):

1. `from = D`, `to = D`: which rows come back?
2. `from = D-1`, `to = D+1`: which rows come back?
3. `from > to`: empty list, or an exception?
4. Ordering of the completion method: which order, and is it guaranteed by the query or by luck?
5. Does any of the above differ between H2 and MySQL? If yes, why.

Predictions go into the debug log entry before the test is run.

## Step 2: integration test

- New class named in the style of the existing ones, for example `BetweenBoundsMySqlIT`, next to
  `HabitCompletionConcurrencyMySqlIT`.
- Must run on a `MySQLContainer<>("mysql:8.0.36")` wired through `@DynamicPropertySource`
  (`spring.datasource.url`, username, password), the same as the existing MySQL ITs. The test
  profile defaults to H2, so without this wiring the test passes without proving anything. The test
  asserts `SELECT VERSION()` reports MySQL, as `HabitCompletionConcurrencyMySqlIT` does.
- Rows are inserted so that each boundary case is distinguishable: a failing inclusive boundary must
  drop a specific, named row.
- One assertion group per prediction above. The `from > to` case asserts whatever was observed, and
  the test name says which.
- Mutation check: change `Between` to a strict comparison in a scratch copy of one method (or query)
  and confirm the test fails; revert. The result is recorded in the debug log entry.

## Step 3: Debug run on real MySQL

1. Docker up; MySQL started from the project's compose or an equivalent container; application
   started in the IDE under the debugger against it (not H2).
2. A daily habit created through the real `POST /habits`; completions and a skip as in Bug 43
   (two series, one bridged by a real skip), with `created_at` adjusted only for the fixture.
3. `GET /habits/{id}/streak-history` with `from` equal to the end of the first series.
4. Breakpoint at the `findByHabitIdAndSkippedOnBetween` call in `HabitQueryService`: record the
   arguments, the thread, and the returned rows. If the Hibernate SQL log can be enabled, record the
   generated `BETWEEN` clause.

## Done when

- Predictions exist in writing and predate the first test run.
- The IT runs green on MySQL and fails under the strict-comparison mutation.
- The Debug run hit its breakpoint on a real request against MySQL; thread and returned rows are
  recorded.
- A `## Bug N` entry is appended to `logs/debug-log.md` (100-day-plan) using the Reproduce / Isolate /
  Hypothesize / Verify structure, and it states explicitly that the Bug 43 H2 caveat is closed (or
  what remains of it).
- Any divergence between H2 and MySQL, or between prediction and observation, is a finding.
- Cold review of the diff before commit; a material finding is recorded in the reflection field
  `Review nalaz`.

## Out of scope

Time zones and `DATETIME` columns (all three columns are `date`), pagination, performance of the
range scans, and the `streak-history` series model.
