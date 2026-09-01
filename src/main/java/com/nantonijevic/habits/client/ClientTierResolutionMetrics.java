package com.nantonijevic.habits.client;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class ClientTierResolutionMetrics {

    private static final String METRIC_NAME =
        "habit.client.tier.resolutions";

    private final Counter resolvedCounter;
    private final Counter rejectedCounter;

    public ClientTierResolutionMetrics(
        MeterRegistry registry
    ) {
        resolvedCounter = counter(
            registry,
            "resolved"
        );

        rejectedCounter = counter(
            registry,
            "rejected"
        );
    }

    public void recordResolved() {
        resolvedCounter.increment();
    }

    public void recordRejected() {
        rejectedCounter.increment();
    }

    private Counter counter(
        MeterRegistry registry,
        String outcome
    ) {
        return Counter
            .builder(METRIC_NAME)
            .description(
                "Number of API key authentication outcomes"
            )
            .tag("outcome", outcome)
            .register(registry);
    }
}
