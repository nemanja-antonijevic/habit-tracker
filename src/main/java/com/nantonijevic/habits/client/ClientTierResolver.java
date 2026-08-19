package com.nantonijevic.habits.client;

import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class ClientTierResolver {

    private final ApiClientRepository repository;

    public ClientTierResolver(
        ApiClientRepository repository
    ) {
        this.repository = repository;
    }

    public ClientTier resolve(Optional<String> apiKey) {
        return apiKey
            .flatMap(repository::findByApiKey)
            .map(ApiClient::getTier)
            .orElse(ClientTier.PUBLIC);
    }
}
