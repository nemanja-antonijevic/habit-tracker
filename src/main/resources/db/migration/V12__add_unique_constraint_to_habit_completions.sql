ALTER TABLE habit_completions ADD CONSTRAINT uq_completions_habit_completed UNIQUE (habit_id, completed_on);
