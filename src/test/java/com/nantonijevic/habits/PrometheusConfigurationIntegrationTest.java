package com.nantonijevic.habits;

import com.nantonijevic.habits.client.ApiClient;
import com.nantonijevic.habits.client.ApiClientRepository;
import com.nantonijevic.habits.client.ApiKeyHasher;
import com.nantonijevic.habits.client.ClientTier;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "spring.kafka.listener.auto-startup=false",
    "management.endpoints.web.exposure.include="
        + "health,metrics,prometheus"
})
@AutoConfigureObservability
@AutoConfigureMockMvc
class PrometheusConfigurationIntegrationTest {

    private static final String EXPOSURE_PROPERTY =
        "management.endpoints.web.exposure.include";

    private static final String METRIC_NAME =
        "habit.client.tier.resolutions";

    private static final String PROMETHEUS_METRIC_NAME =
        "habit_client_tier_resolutions_total";

    private static final String RAW_API_KEY =
        "prometheus-resolved-key";

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApiClientRepository apiClientRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ApiKeyHasher apiKeyHasher;

    private ApiClient savedClient;

    @AfterEach
    void deleteSavedClient() {
        if (savedClient != null) {
            jdbcTemplate.update(
                "DELETE FROM habit_completion_stats"
            );
            jdbcTemplate.update(
                "DELETE FROM habit_completions"
            );
            jdbcTemplate.update(
                "DELETE FROM habits"
            );
            apiClientRepository.delete(savedClient);
            apiClientRepository.flush();
            savedClient = null;
        }
    }

    @Test
    void mainApplicationConfigurationKeepsPrometheusScrapeAvailable()
        throws IOException {

        String exposedEndpoints =
            mainApplicationProperty(EXPOSURE_PROPERTY);

        assertThat(exposedEndpoints)
            .as(
                "main application.yml must expose prometheus"
            )
            .isNotNull();

        assertThat(
            Arrays.stream(
                    exposedEndpoints.split(",")
                )
                .map(String::trim)
                .toList()
        )
            .contains("prometheus");

        String prometheusEnabled =
            mainApplicationProperty(
                "management.endpoint.prometheus.enabled"
            );

        assertThat(
            prometheusEnabled == null
                || Boolean.parseBoolean(prometheusEnabled)
        )
            .as(
                "main application.yml must not disable "
                    + "the prometheus endpoint"
            )
            .isTrue();

        String actuatorBasePath =
            mainApplicationProperty(
                "management.endpoints.web.base-path"
            );

        assertThat(actuatorBasePath)
            .as(
                "main application.yml must keep the expected "
                    + "/actuator scrape base path"
            )
            .isIn(null, "/actuator");
    }

    @Test
    void prometheusRegistryIsInstalled() {
        assertThat(
            applicationContext
                .getBeansOfType(MeterRegistry.class)
                .values()
        )
            .extracting(
                registry ->
                    registry.getClass().getName()
            )
            .contains(
                "io.micrometer.prometheus."
                    + "PrometheusMeterRegistry"
            );
    }

    @Test
    void prometheusEndpointExportsResolvedApiKeyOutcome()
        throws Exception {

        savedClient = apiClientRepository.saveAndFlush(
            new ApiClient(
                apiKeyHasher.hash(RAW_API_KEY),
                ClientTier.INTERNAL,
                "Prometheus test client",
                Instant.parse(
                    "2026-08-26T08:00:00Z"
                )
            )
        );

        double countBefore = resolvedCount();

        mockMvc.perform(
                get("/habits")
                    .header("X-Api-Key", RAW_API_KEY)
            )
            .andExpect(status().isOk());

        double countAfter = resolvedCount();

        assertThat(countAfter)
            .isEqualTo(countBefore + 1.0);

        String scrapeBody = mockMvc.perform(
                get("/actuator/prometheus")
            )
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

        assertThat(scrapeBody.lines())
            .anySatisfy(line -> {
                assertThat(line)
                    .startsWith(PROMETHEUS_METRIC_NAME)
                    .contains("outcome=\"resolved\"")
                    .endsWith(" " + countAfter);
            });
    }

    private double resolvedCount() {
        return meterRegistry
            .get(METRIC_NAME)
            .tag("outcome", "resolved")
            .counter()
            .count();
    }

    private String mainApplicationProperty(
        String propertyName
    ) throws IOException {

        return new YamlPropertySourceLoader()
            .load(
                "main-application",
                new FileSystemResource(
                    "src/main/resources/application.yml"
                )
            )
            .stream()
            .map(source ->
                source.getProperty(propertyName)
            )
            .filter(value -> value != null)
            .map(Object::toString)
            .findFirst()
            .orElse(null);
    }
}
