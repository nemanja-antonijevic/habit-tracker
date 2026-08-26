package com.nantonijevic.habits.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.Instant;
import java.util.Optional;

import static com.nantonijevic.habits.config.RedisCacheConfig.API_CLIENT_TIERS_CACHE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(
    classes = ClientTierCacheTest.TestConfiguration.class
)
class ClientTierCacheTest {

    private static final ApiKeyHasher HASHER =
        new ApiKeyHasher();

    @Autowired
    private ClientTierResolver resolver;

    @Autowired
    private ApiClientRepository repository;

    @Autowired
    private CacheManager cacheManager;

    @BeforeEach
    void clearState() {
        reset(repository);

        cacheManager
            .getCache(API_CLIENT_TIERS_CACHE)
            .clear();
    }

    @Test
    void repeatedKnownKeyQueriesDatabaseOnce() {
        String apiKeyHash = hash("internal-key");

        when(repository.findByApiKeyHash(apiKeyHash))
            .thenReturn(
                Optional.of(
                    client(
                        apiKeyHash,
                        ClientTier.INTERNAL
                    )
                )
            );

        assertThat(resolve("internal-key"))
            .isEqualTo(ClientTier.INTERNAL);

        assertThat(resolve("internal-key"))
            .isEqualTo(ClientTier.INTERNAL);

        verify(repository, times(1))
            .findByApiKeyHash(apiKeyHash);
    }

    @Test
    void differentKeysKeepDifferentCachedTiers() {
        String internalHash = hash("internal-key");
        String trustedHash = hash("trusted-key");

        when(repository.findByApiKeyHash(internalHash))
            .thenReturn(
                Optional.of(
                    client(
                        internalHash,
                        ClientTier.INTERNAL
                    )
                )
            );

        when(repository.findByApiKeyHash(trustedHash))
            .thenReturn(
                Optional.of(
                    client(
                        trustedHash,
                        ClientTier.TRUSTED
                    )
                )
            );

        assertThat(resolve("internal-key"))
            .isEqualTo(ClientTier.INTERNAL);

        assertThat(resolve("trusted-key"))
            .isEqualTo(ClientTier.TRUSTED);

        assertThat(resolve("internal-key"))
            .isEqualTo(ClientTier.INTERNAL);

        assertThat(resolve("trusted-key"))
            .isEqualTo(ClientTier.TRUSTED);

        verify(repository, times(1))
            .findByApiKeyHash(internalHash);

        verify(repository, times(1))
            .findByApiKeyHash(trustedHash);
    }

    @Test
    void unknownKeyIsNotCached() {
        String apiKeyHash = hash("unknown-key");

        when(repository.findByApiKeyHash(apiKeyHash))
            .thenReturn(Optional.empty());

        assertThatThrownBy(
            () -> resolve("unknown-key")
        ).isInstanceOf(InvalidApiKeyException.class);

        assertThatThrownBy(
            () -> resolve("unknown-key")
        ).isInstanceOf(InvalidApiKeyException.class);

        verify(repository, times(2))
            .findByApiKeyHash(apiKeyHash);
    }

    private ClientTier resolve(String apiKey) {
        return resolver.resolve(
            Optional.of(apiKey)
        );
    }

    private static String hash(String apiKey) {
        return HASHER.hash(apiKey);
    }

    private static ApiClient client(
        String apiKeyHash,
        ClientTier tier
    ) {
        return new ApiClient(
            apiKeyHash,
            tier,
            "Test client",
            Instant.parse(
                "2026-08-21T08:00:00Z"
            )
        );
    }

    @Configuration
    @EnableCaching
    @Import({
        ApiKeyHasher.class,
        ClientTierLookup.class,
        ClientTierResolver.class
    })
    static class TestConfiguration {

        @Bean
        ApiClientRepository apiClientRepository() {
            return mock(ApiClientRepository.class);
        }

        @Bean
        ClientTierResolutionMetrics clientTierResolutionMetrics() {
            return mock(
                ClientTierResolutionMetrics.class
            );
        }

        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager(
                API_CLIENT_TIERS_CACHE
            );
        }
    }
}
