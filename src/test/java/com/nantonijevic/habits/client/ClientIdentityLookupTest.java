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
    classes =
        ClientIdentityLookupTest.TestConfiguration.class
)
class ClientIdentityLookupTest {

    @Autowired
    private ApiClientRepository repository;

    @Autowired
    private ClientIdentityLookup lookup;

    @BeforeEach
    void resetRepository() {
        reset(repository);
    }

    @Test
    void activeClientResolvesIdentityWithoutCaching() {
        ApiClient client = mock(ApiClient.class);

        when(client.isActive()).thenReturn(true);
        when(client.getId()).thenReturn(42L);
        when(client.getTier())
            .thenReturn(ClientTier.INTERNAL);
        when(repository.findByApiKeyHash("known-hash"))
            .thenReturn(Optional.of(client));

        assertThat(lookup.resolveByHash("known-hash"))
            .isEqualTo(
                new ClientContext(
                    42L,
                    ClientTier.INTERNAL
                )
            );

        assertThat(lookup.resolveByHash("known-hash"))
            .isEqualTo(
                new ClientContext(
                    42L,
                    ClientTier.INTERNAL
                )
            );

        verify(repository, times(2))
            .findByApiKeyHash("known-hash");
    }

    @Test
    void inactiveClientIsRejected() {
        ApiClient client = mock(ApiClient.class);

        when(client.isActive()).thenReturn(false);
        when(repository.findByApiKeyHash("revoked-hash"))
            .thenReturn(Optional.of(client));

        assertThatThrownBy(
            () -> lookup.resolveByHash("revoked-hash")
        )
            .isInstanceOf(InvalidApiKeyException.class)
            .hasMessage("Invalid API key");
    }

    @Test
    void unknownClientIsRejected() {
        when(repository.findByApiKeyHash("unknown-hash"))
            .thenReturn(Optional.empty());

        assertThatThrownBy(
            () -> lookup.resolveByHash("unknown-hash")
        )
            .isInstanceOf(InvalidApiKeyException.class)
            .hasMessage("Invalid API key");
    }

    @Configuration
    @EnableCaching
    @Import(ClientIdentityLookup.class)
    static class TestConfiguration {

        @Bean
        ApiClientRepository apiClientRepository() {
            return mock(ApiClientRepository.class);
        }

        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager();
        }
    }
}
