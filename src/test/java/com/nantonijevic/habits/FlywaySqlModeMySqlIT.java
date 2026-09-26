package com.nantonijevic.habits;

import org.flywaydb.core.api.callback.BaseCallback;
import org.flywaydb.core.api.callback.Context;
import org.flywaydb.core.api.callback.Event;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@TestPropertySource(properties = {
    "spring.kafka.listener.auto-startup=false",
    "spring.cache.type=none"
})
@Import(
    FlywaySqlModeMySqlIT.CallbackConfiguration.class
)
class FlywaySqlModeMySqlIT
    extends AbstractIntegrationTest {

    static final String EXPECTED_SQL_MODE =
        "ONLY_FULL_GROUP_BY,"
            + "STRICT_TRANS_TABLES,"
            + "NO_ZERO_IN_DATE,"
            + "NO_ZERO_DATE,"
            + "ERROR_FOR_DIVISION_BY_ZERO,"
            + "NO_ENGINE_SUBSTITUTION";

    @Container
    static final MySQLContainer<?> MYSQL =
        new MySQLContainer<>(
            "mysql:8.0.36"
        )
            .withDatabaseName("habits")
            .withUsername("habits")
            .withPassword("habits")
            .withCommand("--sql-mode=");

    @DynamicPropertySource
    static void configureMySql(
        DynamicPropertyRegistry registry
    ) {
        registry.add(
            "spring.datasource.url",
            FlywaySqlModeMySqlIT::pinnedJdbcUrl
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
    private SqlModeCaptureCallback callback;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void pinsMeasuredGoodSqlModeOnFlywayAndApplicationConnections() {
        assertThat(callback.globalSqlMode())
            .as("container global sql_mode")
            .isEmpty();

        assertThat(callback.sessionSqlMode())
            .as("Flyway connection sql_mode")
            .isEqualTo(EXPECTED_SQL_MODE);

        String applicationSqlMode =
            jdbcTemplate.queryForObject(
                "SELECT @@SESSION.sql_mode",
                String.class
            );

        assertThat(applicationSqlMode)
            .as("application datasource sql_mode")
            .isEqualTo(EXPECTED_SQL_MODE);
    }

    private static String pinnedJdbcUrl() {
        String jdbcUrl = MYSQL.getJdbcUrl();

        String separator =
            jdbcUrl.contains("?") ? "&" : "?";

        return jdbcUrl
            + separator
            + "sessionVariables=sql_mode='"
            + EXPECTED_SQL_MODE
            + "'";
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class CallbackConfiguration {

        @Bean
        SqlModeCaptureCallback sqlModeCaptureCallback() {
            return new SqlModeCaptureCallback();
        }
    }

    static class SqlModeCaptureCallback
        extends BaseCallback {

        private String globalSqlMode;
        private String sessionSqlMode;

        @Override
        public boolean supports(
            Event event,
            Context context
        ) {
            return event == Event.BEFORE_MIGRATE;
        }

        @Override
        public void handle(
            Event event,
            Context context
        ) {
            try (
                Statement statement =
                    context
                        .getConnection()
                        .createStatement();

                ResultSet resultSet =
                    statement.executeQuery(
                        """
                        SELECT
                            @@GLOBAL.sql_mode,
                            @@SESSION.sql_mode
                        """
                    )
            ) {
                if (!resultSet.next()) {
                    throw new IllegalStateException(
                        "MySQL did not return sql_mode"
                    );
                }

                globalSqlMode =
                    resultSet.getString(1);

                sessionSqlMode =
                    resultSet.getString(2);
            } catch (SQLException exception) {
                throw new IllegalStateException(
                    "Could not read Flyway sql_mode",
                    exception
                );
            }
        }

        String globalSqlMode() {
            return globalSqlMode;
        }

        String sessionSqlMode() {
            return sessionSqlMode;
        }
    }
}
