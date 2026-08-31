-- Expand step of ADR 0005. owner_id is deliberately nullable: NOT NULL here
-- succeeds on MySQL and silently assigns owner 0, which the FK then rejects (1452).
ALTER TABLE api_clients
    ADD COLUMN active boolean NOT NULL DEFAULT TRUE;

ALTER TABLE habits
    ADD COLUMN owner_id bigint;

CREATE INDEX idx_habits_owner ON habits (owner_id);

-- RESTRICT, not CASCADE: deleting a client must never delete a user's habits.
ALTER TABLE habits
    ADD CONSTRAINT fk_habits_owner
        FOREIGN KEY (owner_id) REFERENCES api_clients (id)
            ON DELETE RESTRICT;
