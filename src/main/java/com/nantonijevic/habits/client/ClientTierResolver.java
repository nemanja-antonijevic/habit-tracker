package com.nantonijevic.habits.client;

import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class ClientTierResolver {

    private final ApiKeyHasher hasher;
    private final ClientTierLookup lookup;

    public ClientTierResolver(
        ApiKeyHasher hasher,
        ClientTierLookup lookup
    ) {
        this.hasher = hasher;
        this.lookup = lookup;
    }

    public ClientTier resolve(Optional<String> apiKey) {
        if (apiKey.isEmpty()) {
            return ClientTier.PUBLIC;
        }

        String apiKeyHash = hasher.hash(
            apiKey.get()
        );

        return lookup.resolveByHash(apiKeyHash);
    }
}
