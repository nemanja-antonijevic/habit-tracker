package com.nantonijevic.habits.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClientTierResolverTest {

    @Mock
    private ApiClientRepository repository;

    @InjectMocks
    private ClientTierResolver resolver;

    @Test
    void missingApiKeyFallsBackToPublicWithoutDatabaseLookup() {
        ClientTier tier = resolver.resolve(Optional.empty());

        assertThat(tier).isEqualTo(ClientTier.PUBLIC);
        verifyNoInteractions(repository);
    }

    @Test
    void unknownApiKeyIsRejectedAfterDatabaseLookup() {
        when(repository.findByApiKey("unknown-key"))
            .thenReturn(Optional.empty());

        assertThatThrownBy(
            () -> resolver.resolve(
                Optional.of("unknown-key")
            )
        )
            .isInstanceOf(InvalidApiKeyException.class)
            .hasMessage("Invalid API key");

        verify(repository)
            .findByApiKey("unknown-key");
    }

    @Test
    void knownApiKeyUsesTierStoredInDatabase() {
        ApiClient client = new ApiClient(
            "trusted-key",
            ClientTier.TRUSTED,
            "Trusted client",
            Instant.parse("2026-08-19T08:00:00Z")
        );

        when(repository.findByApiKey("trusted-key"))
            .thenReturn(Optional.of(client));

        ClientTier tier = resolver.resolve(
            Optional.of("trusted-key")
        );

        assertThat(tier).isEqualTo(ClientTier.TRUSTED);

        verify(repository)
            .findByApiKey("trusted-key");
    }
}
