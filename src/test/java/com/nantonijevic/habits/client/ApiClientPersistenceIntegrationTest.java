package com.nantonijevic.habits.client;

import com.nantonijevic.habits.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class ApiClientPersistenceIntegrationTest
    extends AbstractIntegrationTest {

    private static final String API_KEY_HASH =
        "a".repeat(64);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ApiClientRepository repository;

    @Test
    void readsInactiveClientFromDatabase() {
        jdbcTemplate.update(
            """
            INSERT INTO api_clients (
                api_key_hash,
                tier,
                name,
                created_at,
                active
            )
            VALUES (?, 'INTERNAL', 'Revoked client',
                    CURRENT_TIMESTAMP, FALSE)
            """,
            API_KEY_HASH
        );

        ApiClient client = repository
            .findByApiKeyHash(API_KEY_HASH)
            .orElseThrow();

        assertThat(client.isActive())
            .isFalse();
    }
}
