package com.nantonijevic.habits.client;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClientTierResolutionMetricsTest {

    private SimpleMeterRegistry registry;

    private ClientTierResolutionMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new ClientTierResolutionMetrics(
            registry
        );
    }

    @Test
    void doesNotPublishRetiredAnonymousPublicOutcome() {
        assertThat(
            registry
                .find("habit.client.tier.resolutions")
                .tag("outcome", "public")
                .counter()
        ).isNull();
    }

    @Test
    void recordsResolvedApiKey() {
        metrics.recordResolved();

        assertThat(count("resolved"))
            .isEqualTo(1.0);

        assertThat(count("rejected"))
            .isZero();
    }

    @Test
    void recordsRejectedApiKey() {
        metrics.recordRejected();

        assertThat(count("resolved"))
            .isZero();

        assertThat(count("rejected"))
            .isEqualTo(1.0);
    }

    private double count(String outcome) {
        return registry
            .get("habit.client.tier.resolutions")
            .tag("outcome", outcome)
            .counter()
            .count();
    }
}
