-- Recreated, not altered: api_clients was measured empty, and MODIFY COLUMN
-- differs across MySQL and H2. Provisioned keys are lost here, silently.
DROP TABLE api_clients;

CREATE TABLE api_clients
(
    id           bigint       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    api_key_hash char(64)     NOT NULL,
    tier         varchar(32)  NOT NULL,
    name         varchar(255) NOT NULL,
    created_at   datetime(6)  NOT NULL,

    CONSTRAINT uq_api_clients_api_key_hash
        UNIQUE (api_key_hash),

    CONSTRAINT chk_api_clients_api_key_hash_length
        CHECK (CHAR_LENGTH(api_key_hash) = 64)
);
