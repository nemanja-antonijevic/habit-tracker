package com.nantonijevic.habits.client;

import com.nantonijevic.habits.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

@Testcontainers
@TestPropertySource(properties = {
    "spring.kafka.listener.auto-startup=false",
    "spring.cache.type=none"
})
class ApiClientHashConstraintMySqlIT
    extends AbstractIntegrationTest {

    private static final int CHECK_CONSTRAINT_ERROR_CODE =
        3819;

    private static final String CONSTRAINT_NAME =
        "chk_api_clients_api_key_hash_length";

    @Container
    static final MySQLContainer<?> MYSQL =
        new MySQLContainer<>(
            "mysql:8.0.36"
        )
            .withDatabaseName("habits")
            .withUsername("habits")
            .withPassword("habits");

    @DynamicPropertySource
    static void configureMySql(
        DynamicPropertyRegistry registry
    ) {
        registry.add(
            "spring.datasource.url",
            MYSQL::getJdbcUrl
        );
        registry.add(
            "spring.datasource.username",
            MYSQL::getUsername
        );
        registry.add(
            "spring.datasource.password",
            MYSQL::getPassword
        );
        registry.add(
            "spring.datasource.driver-class-name",
            MYSQL::getDriverClassName
        );
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void rejectsApiKeyHashThatIsNotSha256Length() {
        Throwable thrown = catchThrowable(
            () -> jdbcTemplate.update(
                """
                INSERT INTO api_clients (
                    api_key_hash,
                    tier,
                    name,
                    created_at
                )
                VALUES (
                    'abc',
                    'PUBLIC',
                    'Invalid short hash',
                    CURRENT_TIMESTAMP
                )
                """
            )
        );

        assertThat(thrown)
            .isNotNull();

        assertThat(rootCause(thrown))
            .isInstanceOfSatisfying(
                SQLException.class,
                sqlException -> {
                    assertThat(
                        sqlException.getErrorCode()
                    ).isEqualTo(
                        CHECK_CONSTRAINT_ERROR_CODE
                    );

                    assertThat(
                        sqlException.getMessage()
                    ).contains(CONSTRAINT_NAME);
                }
            );
    }

    private static Throwable rootCause(
        Throwable throwable
    ) {
        Throwable result = throwable;

        while (result.getCause() != null) {
            result = result.getCause();
        }

        return result;
    }
}
