package com.nantonijevic.habits.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class HabitCompletionMetrics {

    private static final String METRIC_NAME =
        "habit.completions";

    private final Counter firstAttemptCounter;
    private final Counter retriedCounter;
    private final Counter conflictExhaustedCounter;

    public HabitCompletionMetrics(
        MeterRegistry registry
    ) {
        firstAttemptCounter = counter(
            registry,
            "first_attempt"
        );

        retriedCounter = counter(
            registry,
            "retried"
        );

        conflictExhaustedCounter = counter(
            registry,
            "conflict_exhausted"
        );
    }

    public void recordFirstAttempt() {
        firstAttemptCounter.increment();
    }

    public void recordRetried() {
        retriedCounter.increment();
    }

    public void recordConflictExhausted() {
        conflictExhaustedCounter.increment();
    }

    private Counter counter(
        MeterRegistry registry,
        String outcome
    ) {
        return Counter
            .builder(METRIC_NAME)
            .description(
                "Number of single-habit completion command outcomes"
            )
            .tag("outcome", outcome)
            .register(registry);
    }
}
