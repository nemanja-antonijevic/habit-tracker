package com.nantonijevic.habits.client;

import org.springframework.stereotype.Component;

@Component
public class ClientIdentityLookup {

    private final ApiClientRepository repository;

    public ClientIdentityLookup(
        ApiClientRepository repository
    ) {
        this.repository = repository;
    }

    public ClientContext resolveByHash(
        String apiKeyHash
    ) {
        return repository
            .findByApiKeyHash(apiKeyHash)
            .filter(ApiClient::isActive)
            .map(
                client -> new ClientContext(
                    client.getId(),
                    client.getTier()
                )
            )
            .orElseThrow(InvalidApiKeyException::new);
    }
}
