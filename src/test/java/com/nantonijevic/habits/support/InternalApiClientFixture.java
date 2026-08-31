package com.nantonijevic.habits.support;

import com.nantonijevic.habits.client.ApiClient;
import com.nantonijevic.habits.client.ApiClientRepository;
import com.nantonijevic.habits.client.ApiKeyHasher;
import com.nantonijevic.habits.client.ClientTier;
import org.springframework.boot.test.context.TestComponent;

import java.time.Instant;
import java.util.UUID;

@TestComponent
public class InternalApiClientFixture {

    private static final Instant CREATED_AT =
        Instant.parse("2026-08-31T08:00:00Z");

    private final ApiClientRepository repository;
    private final ApiKeyHasher apiKeyHasher;

    public InternalApiClientFixture(
        ApiClientRepository repository,
        ApiKeyHasher apiKeyHasher
    ) {
        this.repository = repository;
        this.apiKeyHasher = apiKeyHasher;
    }

    public String provisionInternalClient() {
        String rawApiKey =
            "integration-internal-" + UUID.randomUUID();

        repository.saveAndFlush(
            new ApiClient(
                apiKeyHasher.hash(rawApiKey),
                ClientTier.INTERNAL,
                "Integration test client",
                CREATED_AT
            )
        );

        return rawApiKey;
    }
}
