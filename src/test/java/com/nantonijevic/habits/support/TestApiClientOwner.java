package com.nantonijevic.habits.support;

import org.springframework.jdbc.core.JdbcTemplate;

public final class TestApiClientOwner {

    private TestApiClientOwner() {
    }

    public static void ensureExists(
        JdbcTemplate jdbcTemplate,
        Long ownerId
    ) {
        jdbcTemplate.update(
            """
            MERGE INTO api_clients (
                id,
                api_key_hash,
                tier,
                name,
                created_at,
                active
            )
            KEY (id)
            VALUES (?, ?, 'INTERNAL',
                    'Direct integration test owner',
                    CURRENT_TIMESTAMP, TRUE)
            """,
            ownerId,
            "c".repeat(64)
        );
    }
}
