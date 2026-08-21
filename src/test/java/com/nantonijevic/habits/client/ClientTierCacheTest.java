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

    private static final String CACHE_NAME =
        "api-client-tiers";

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
            .getCache(CACHE_NAME)
            .clear();
    }

    @Test
    void repeatedKnownKeyQueriesDatabaseOnce() {
        when(repository.findByApiKey("internal-key"))
            .thenReturn(
                Optional.of(
                    client(
                        "internal-key",
                        ClientTier.INTERNAL
                    )
                )
            );

        assertThat(
            resolver.resolve(
                Optional.of("internal-key")
            )
        ).isEqualTo(ClientTier.INTERNAL);

        assertThat(
            resolver.resolve(
                Optional.of("internal-key")
            )
        ).isEqualTo(ClientTier.INTERNAL);

        verify(repository, times(1))
            .findByApiKey("internal-key");
    }

    @Test
    void differentKeysKeepDifferentCachedTiers() {
        when(repository.findByApiKey("internal-key"))
            .thenReturn(
                Optional.of(
                    client(
                        "internal-key",
                        ClientTier.INTERNAL
                    )
                )
            );

        when(repository.findByApiKey("trusted-key"))
            .thenReturn(
                Optional.of(
                    client(
                        "trusted-key",
                        ClientTier.TRUSTED
                    )
                )
            );

        assertThat(
            resolver.resolve(
                Optional.of("internal-key")
            )
        ).isEqualTo(ClientTier.INTERNAL);

        assertThat(
            resolver.resolve(
                Optional.of("trusted-key")
            )
        ).isEqualTo(ClientTier.TRUSTED);

        assertThat(
            resolver.resolve(
                Optional.of("internal-key")
            )
        ).isEqualTo(ClientTier.INTERNAL);

        assertThat(
            resolver.resolve(
                Optional.of("trusted-key")
            )
        ).isEqualTo(ClientTier.TRUSTED);

        verify(repository, times(1))
            .findByApiKey("internal-key");

        verify(repository, times(1))
            .findByApiKey("trusted-key");
    }

    @Test
    void unknownKeyIsNotCached() {
        when(repository.findByApiKey("unknown-key"))
            .thenReturn(Optional.empty());

        assertThatThrownBy(
            () -> resolver.resolve(
                Optional.of("unknown-key")
            )
        ).isInstanceOf(InvalidApiKeyException.class);

        assertThatThrownBy(
            () -> resolver.resolve(
                Optional.of("unknown-key")
            )
        ).isInstanceOf(InvalidApiKeyException.class);

        verify(repository, times(2))
            .findByApiKey("unknown-key");
    }

    private static ApiClient client(
        String apiKey,
        ClientTier tier
    ) {
        return new ApiClient(
            apiKey,
            tier,
            "Test client",
            Instant.parse(
                "2026-08-21T08:00:00Z"
            )
        );
    }

    @Configuration
    @EnableCaching
    @Import(ClientTierResolver.class)
    static class TestConfiguration {

        @Bean
        ApiClientRepository apiClientRepository() {
            return mock(ApiClientRepository.class);
        }

        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager(
                CACHE_NAME
            );
        }
    }
}
