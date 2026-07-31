-- Performance fixture: 200 habits across 5 distinct schedules.
-- Schedules must differ, otherwise the schedule-filtering path is one case measured 200 times.
-- Run before seed-history.sql. Re-runnable: everything under the 'JFR Seed %' namespace is replaced.

START TRANSACTION;

DELETE s
FROM habit_completion_stats s
JOIN habits h ON h.id = s.habit_id
WHERE h.name LIKE 'JFR Seed %';

DELETE c
FROM habit_completions c
JOIN habits h ON h.id = c.habit_id
WHERE h.name LIKE 'JFR Seed %';

DELETE FROM habits
WHERE name LIKE 'JFR Seed %';

INSERT INTO habits (
    name,
    created_at,
    completion_count,
    last_completed_at,
    version,
    current_streak,
    longest_streak,
    archived,
    scheduled_days
)
WITH RECURSIVE sequence(n) AS (
    SELECT 1
    UNION ALL
    SELECT n + 1
    FROM sequence
    WHERE n < 200
)
SELECT
    CONCAT('JFR Seed ', LPAD(n, 3, '0')),
    TIMESTAMP('2024-08-01 12:00:00'),
    0,
    NULL,
    0,
    0,
    0,
    FALSE,
    CASE MOD(n, 5)
    WHEN 0 THEN 'MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY,SATURDAY,SUNDAY'
    WHEN 1 THEN 'MONDAY,WEDNESDAY,FRIDAY'
    WHEN 2 THEN 'TUESDAY,THURSDAY'
    WHEN 3 THEN 'SATURDAY,SUNDAY'
    ELSE 'MONDAY'
END
FROM sequence;

COMMIT;
