-- The 'c' match_type forces case-sensitive matching on MySQL, whose default
-- collation would otherwise accept 64 uppercase hex characters. H2 rejects
-- them either way, so only ApiClientHashConstraintMySqlIT proves the flag.
ALTER TABLE api_clients
    ADD CONSTRAINT chk_api_clients_api_key_hash_format
        CHECK (
            REGEXP_LIKE(
                    api_key_hash,
                    '^[0-9a-f]{64}$',
                    'c'
            )
            );
