ALTER TABLE habit_completion_stats ADD CONSTRAINT uq_stats_habit_completed UNIQUE (habit_id, completed_on);
