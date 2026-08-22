package com.nantonijevic.habits.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClientTierResolverTest {

    @Mock
    private ApiKeyHasher hasher;

    @Mock
    private ClientTierLookup lookup;

    @InjectMocks
    private ClientTierResolver resolver;

    @Test
    void missingApiKeyFallsBackToPublicWithoutLookup() {
        ClientTier tier = resolver.resolve(Optional.empty());

        assertThat(tier).isEqualTo(ClientTier.PUBLIC);

        verifyNoInteractions(hasher, lookup);
    }

    @Test
    void unknownApiKeyIsRejectedAfterHashedLookup() {
        when(hasher.hash("unknown-key"))
            .thenReturn("unknown-hash");

        when(lookup.resolveByHash("unknown-hash"))
            .thenThrow(new InvalidApiKeyException());

        assertThatThrownBy(
            () -> resolver.resolve(
                Optional.of("unknown-key")
            )
        )
            .isInstanceOf(InvalidApiKeyException.class)
            .hasMessage("Invalid API key");

        verify(hasher).hash("unknown-key");
        verify(lookup).resolveByHash("unknown-hash");
    }

    @Test
    void knownApiKeyUsesTierResolvedByHash() {
        when(hasher.hash("trusted-key"))
            .thenReturn("trusted-hash");

        when(lookup.resolveByHash("trusted-hash"))
            .thenReturn(ClientTier.TRUSTED);

        ClientTier tier = resolver.resolve(
            Optional.of("trusted-key")
        );

        assertThat(tier).isEqualTo(ClientTier.TRUSTED);

        verify(hasher).hash("trusted-key");
        verify(lookup).resolveByHash("trusted-hash");
    }
}
