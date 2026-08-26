package com.nantonijevic.habits.client;

import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class ClientTierResolver {

    private final ApiKeyHasher hasher;
    private final ClientTierLookup lookup;
    private final ClientTierResolutionMetrics metrics;

    public ClientTierResolver(
        ApiKeyHasher hasher,
        ClientTierLookup lookup,
        ClientTierResolutionMetrics metrics
    ) {
        this.hasher = hasher;
        this.lookup = lookup;
        this.metrics = metrics;
    }

    public ClientTier resolve(Optional<String> apiKey) {
        if (apiKey.isEmpty()) {
            metrics.recordPublic();

            return ClientTier.PUBLIC;
        }

        String apiKeyHash = hasher.hash(
            apiKey.get()
        );

        try {
            ClientTier tier =
                lookup.resolveByHash(apiKeyHash);

            metrics.recordResolved();

            return tier;
        } catch (InvalidApiKeyException exception) {
            metrics.recordRejected();

            throw exception;
        }
    }
}
