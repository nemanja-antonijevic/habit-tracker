-- Performance fixture: ~2 years of history (2024-08-01 .. 2026-07-31) for the habits from seed-habits.sql.
-- Both tables are filled on purpose: /history reads habit_completions, while the dashboard and
-- completion-rate read habit_completion_stats. Seeding only one gives a falsely representative profile.
-- Streak values are synthetic (MOD arithmetic), so this bypasses the domain write path -- it is a
-- read-performance fixture and proves nothing about streak correctness.
-- Run after seed-habits.sql. Re-runnable: everything under the 'JFR Seed %' namespace is replaced.
-- Re-running yields a statistically equivalent but NOT identical dataset: the skip pattern below
-- includes h.id, and auto_increment advances when seed-habits.sql re-inserts. For a before/after
-- benchmark, seed once and leave the data in place across both runs.

START TRANSACTION;

DELETE s
FROM habit_completion_stats s
JOIN habits h ON h.id = s.habit_id
WHERE h.name LIKE 'JFR Seed %';

DELETE c
FROM habit_completions c
JOIN habits h ON h.id = c.habit_id
WHERE h.name LIKE 'JFR Seed %';

INSERT INTO habit_completions (
    habit_id,
    completed_on
)
WITH RECURSIVE dates(completed_on) AS (
    SELECT DATE('2024-08-01')
UNION ALL
SELECT completed_on + INTERVAL 1 DAY
FROM dates
WHERE completed_on < DATE('2026-07-31')
    )
SELECT
    h.id,
    dates.completed_on
FROM habits h
         CROSS JOIN dates
WHERE h.name LIKE 'JFR Seed %'
  AND FIND_IN_SET(
              ELT(
                      DAYOFWEEK(dates.completed_on),
                      'SUNDAY',
                      'MONDAY',
                      'TUESDAY',
                      'WEDNESDAY',
                      'THURSDAY',
                      'FRIDAY',
                      'SATURDAY'
              ),
              h.scheduled_days
      ) > 0
  AND MOD(
              DATEDIFF(dates.completed_on, DATE('2024-08-01')) + h.id,
              4
      ) <> 0;

INSERT INTO habit_completion_stats (
    habit_id,
    completed_on,
    current_streak,
    completion_count
)
SELECT
    ranked.habit_id,
    ranked.completed_on,
    1 + MOD(ranked.sequence_number - 1, 12),
    ranked.sequence_number
FROM (
    SELECT
    c.habit_id,
    c.completed_on,
    ROW_NUMBER() OVER (
    PARTITION BY c.habit_id
    ORDER BY c.completed_on
    ) AS sequence_number
    FROM habit_completions c
    JOIN habits h ON h.id = c.habit_id
    WHERE h.name LIKE 'JFR Seed %'
    ) ranked;

UPDATE habits h
    JOIN (
    SELECT
    s.habit_id,
    COUNT(*) AS completion_count,
    MAX(s.completed_on) AS last_completed_on
    FROM habit_completion_stats s
    JOIN habits seeded ON seeded.id = s.habit_id
    WHERE seeded.name LIKE 'JFR Seed %'
    GROUP BY s.habit_id
    ) aggregate ON aggregate.habit_id = h.id
    SET
        h.completion_count = aggregate.completion_count,
        h.last_completed_at =
        TIMESTAMP(aggregate.last_completed_on, '12:00:00'),
        h.current_streak =
        1 + MOD(aggregate.completion_count - 1, 12),
        h.longest_streak = 12;

COMMIT;
