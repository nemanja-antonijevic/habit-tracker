package com.nantonijevic.habits.client;

import com.nantonijevic.habits.config.RedisCacheConfig;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

@Component
public class ClientTierLookup {

    private final ApiClientRepository repository;

    public ClientTierLookup(
        ApiClientRepository repository
    ) {
        this.repository = repository;
    }

    @Cacheable(
        cacheNames =
            RedisCacheConfig.API_CLIENT_TIERS_CACHE,
        key = "#apiKeyHash"
    )
    public ClientTier resolveByHash(
        String apiKeyHash
    ) {
        return repository
            .findByApiKeyHash(apiKeyHash)
            .map(ApiClient::getTier)
            .orElseThrow(InvalidApiKeyException::new);
    }
}
