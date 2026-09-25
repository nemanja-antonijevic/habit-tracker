package com.nantonijevic.habits.service;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HabitCompletionMetricsTest {

    private SimpleMeterRegistry registry;

    private HabitCompletionMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new HabitCompletionMetrics(
            registry
        );
    }

    @Test
    void recordsFirstAttemptOutcome() {
        metrics.recordFirstAttempt();

        assertThat(count("first_attempt"))
            .isEqualTo(1.0);

        assertThat(count("retried"))
            .isZero();

        assertThat(count("conflict_exhausted"))
            .isZero();
    }

    @Test
    void recordsRetriedOutcome() {
        metrics.recordRetried();

        assertThat(count("first_attempt"))
            .isZero();

        assertThat(count("retried"))
            .isEqualTo(1.0);

        assertThat(count("conflict_exhausted"))
            .isZero();
    }

    @Test
    void recordsConflictExhaustedOutcome() {
        metrics.recordConflictExhausted();

        assertThat(count("first_attempt"))
            .isZero();

        assertThat(count("retried"))
            .isZero();

        assertThat(count("conflict_exhausted"))
            .isEqualTo(1.0);
    }

    private double count(String outcome) {
        return registry
            .get("habit.completions")
            .tag("outcome", outcome)
            .counter()
            .count();
    }
}
