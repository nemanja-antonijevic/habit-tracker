package com.nantonijevic.habits;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class ProdJsonLoggingIntegrationTest {

    private static final String INFO_MESSAGE =
        "prod-json-info-sentinel";

    private static final String DEBUG_MESSAGE =
        "prod-json-debug-sentinel";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void prodProfileWritesStructuredJsonToStdout()
        throws Exception {

        Path stdoutFile =
            Files.createTempFile("prod-logging-", ".out");

        Path stderrFile =
            Files.createTempFile("prod-logging-", ".err");

        try {
            Process process = new ProcessBuilder(
                javaExecutable(),
                "-cp",
                testClasspath(),
                ProdLoggingProbe.class.getName()
            )
                .redirectOutput(stdoutFile.toFile())
                .redirectError(stderrFile.toFile())
                .start();

            boolean finished =
                process.waitFor(30, TimeUnit.SECONDS);

            if (!finished) {
                process.destroyForcibly();
                process.waitFor();
            }

            String stdout = Files.readString(stdoutFile);
            String stderr = Files.readString(stderrFile);

            assertThat(finished)
                .as(
                    "Prod logging probe did not finish. stderr:%n%s",
                    stderr
                )
                .isTrue();

            assertThat(process.exitValue())
                .as("Prod logging probe stderr:%n%s", stderr)
                .isZero();

            List<JsonNode> logEntries = stdout.lines()
                .map(this::parseJson)
                .toList();

            assertThat(logEntries).isNotEmpty();

            assertThat(logEntries).allSatisfy(entry -> {
                assertThat(entry.hasNonNull("@timestamp")).isTrue();
                assertThat(entry.hasNonNull("level")).isTrue();
                assertThat(entry.hasNonNull("logger_name")).isTrue();
                assertThat(entry.hasNonNull("message")).isTrue();
            });

            assertThat(logEntries)
                .anySatisfy(entry -> {
                    assertThat(entry.path("message").asText())
                        .isEqualTo(INFO_MESSAGE);

                    assertThat(entry.path("level").asText())
                        .isEqualTo("INFO");

                    assertThat(entry.path("logger_name").asText())
                        .isEqualTo(
                            ProdLoggingProbe.class.getName()
                        );
                });

            assertThat(logEntries)
                .noneSatisfy(entry ->
                    assertThat(entry.path("message").asText())
                        .isEqualTo(DEBUG_MESSAGE)
                );
        } finally {
            Files.deleteIfExists(stdoutFile);
            Files.deleteIfExists(stderrFile);
        }
    }

    private String javaExecutable() {
        return Path.of(
            System.getProperty("java.home"),
            "bin",
            "java"
        ).toString();
    }

    private String testClasspath() {
        return System.getProperty(
            "surefire.test.class.path",
            System.getProperty("java.class.path")
        );
    }

    private JsonNode parseJson(String line) {
        try {
            JsonNode entry = objectMapper.readTree(line);

            if (entry == null || !entry.isObject()) {
                throw new AssertionError(
                    "Prod stdout line is not a JSON object: " + line
                );
            }

            return entry;
        } catch (JsonProcessingException exception) {
            throw new AssertionError(
                "Prod stdout contains a non-JSON line: " + line,
                exception
            );
        }
    }

    public static final class ProdLoggingProbe {

        private static final Logger LOGGER =
            LoggerFactory.getLogger(ProdLoggingProbe.class);

        private ProdLoggingProbe() {
        }

        public static void main(String[] arguments) {
            SpringApplication application =
                new SpringApplication(
                    HabitTrackerApplication.class
                );

            application.setWebApplicationType(
                WebApplicationType.NONE
            );

            try (ConfigurableApplicationContext ignored =
                     application.run(
                         "--spring.profiles.active=prod",
                         "--spring.cache.type=none",
                         "--spring.kafka.listener.auto-startup=false"
                     )) {

                LOGGER.info(INFO_MESSAGE);
                LOGGER.debug(DEBUG_MESSAGE);
            }
        }
    }
}
