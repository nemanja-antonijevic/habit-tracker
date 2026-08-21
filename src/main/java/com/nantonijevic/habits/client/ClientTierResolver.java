package com.nantonijevic.habits.client;

import com.nantonijevic.habits.config.RedisCacheConfig;
import org.springframework.cache.annotation.Cacheable;
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

    @Cacheable(
        cacheNames =
            RedisCacheConfig.API_CLIENT_TIERS_CACHE,
        key = "#apiKey.get()",
        condition = "#apiKey.isPresent()"
    )
    public ClientTier resolve(Optional<String> apiKey) {
        if (apiKey.isEmpty()) {
            return ClientTier.PUBLIC;
        }

        return repository
            .findByApiKey(apiKey.get())
            .map(ApiClient::getTier)
            .orElseThrow(InvalidApiKeyException::new);
    }
}
