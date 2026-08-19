CREATE TABLE api_clients
(
    id         bigint       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    api_key    varchar(128) NOT NULL,
    tier       varchar(32)  NOT NULL,
    name       varchar(255) NOT NULL,
    created_at datetime(6)  NOT NULL,

    CONSTRAINT uq_api_clients_api_key
        UNIQUE (api_key)
);
