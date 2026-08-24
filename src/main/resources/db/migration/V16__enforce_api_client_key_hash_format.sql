ALTER TABLE api_clients
    ADD CONSTRAINT chk_api_clients_api_key_hash_format
        CHECK (
            REGEXP_LIKE(
                    api_key_hash,
                    '^[0-9a-f]{64}$',
                    'c'
            )
            );
