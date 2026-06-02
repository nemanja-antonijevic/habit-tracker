CREATE TABLE habit_completions (
                        id bigint not null auto_increment PRIMARY KEY,
                        habit_id bigint NOT NULL,
                        completed_on date NOT NULL,
                        CONSTRAINT fk_habit_completions_habit FOREIGN KEY (habit_id) REFERENCES habits(id)
);
