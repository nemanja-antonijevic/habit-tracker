CREATE TABLE habit_skips
(
    id          bigint NOT NULL AUTO_INCREMENT PRIMARY KEY,
    habit_id    bigint NOT NULL,
    skipped_on  date   NOT NULL,
    month_start date   NOT NULL,

    CONSTRAINT uq_habit_skips_habit_month
        UNIQUE (habit_id, month_start),

    CONSTRAINT fk_habit_skips_habit
        FOREIGN KEY (habit_id)
            REFERENCES habits (id)
            ON DELETE CASCADE
);
