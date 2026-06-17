CREATE TABLE habit_completion_stats
(
    id               bigint    not null auto_increment PRIMARY KEY,
    habit_id         bigint    NOT NULL,
    completed_on     date      NOT NULL,
    current_streak   int       NOT NULL,
    completion_count int       NOT NULL,
    recorded_at      timestamp NOT NULL
);
