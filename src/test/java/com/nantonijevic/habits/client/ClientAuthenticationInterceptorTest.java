package com.nantonijevic.habits.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClientAuthenticationInterceptorTest {

    @Mock
    private ApiKeyHasher hasher;

    @Mock
    private ClientIdentityLookup lookup;

    @Mock
    private ClientTierResolutionMetrics metrics;

    @InjectMocks
    private ClientAuthenticationInterceptor interceptor;

    @Test
    void activeClientIsStoredAsRequestContext() {
        MockHttpServletRequest request =
            new MockHttpServletRequest();

        ClientContext context =
            new ClientContext(
                42L,
                ClientTier.INTERNAL
            );

        request.addHeader(
            ClientTierArgumentResolver.API_KEY_HEADER,
            "known-key"
        );

        when(hasher.hash("known-key"))
            .thenReturn("known-hash");

        when(lookup.resolveByHash("known-hash"))
            .thenReturn(context);

        boolean allowed = interceptor.preHandle(
            request,
            new MockHttpServletResponse(),
            new Object()
        );

        assertThat(allowed).isTrue();

        assertThat(
            request.getAttribute(
                ClientAuthenticationInterceptor
                    .CLIENT_CONTEXT_ATTRIBUTE
            )
        ).isEqualTo(context);

        verify(metrics).recordResolved();
        verifyNoMoreInteractions(metrics);
    }

    @Test
    void missingApiKeyIsRejectedBeforeLookup() {
        MockHttpServletRequest request =
            new MockHttpServletRequest();

        assertThatThrownBy(
            () -> interceptor.preHandle(
                request,
                new MockHttpServletResponse(),
                new Object()
            )
        )
            .isInstanceOf(InvalidApiKeyException.class)
            .hasMessage("Invalid API key");

        verifyNoInteractions(hasher, lookup);
        verify(metrics).recordRejected();
        verifyNoMoreInteractions(metrics);
    }

    @Test
    void unknownOrInactiveApiKeyIsRejected() {
        MockHttpServletRequest request =
            new MockHttpServletRequest();

        request.addHeader(
            ClientTierArgumentResolver.API_KEY_HEADER,
            "rejected-key"
        );

        when(hasher.hash("rejected-key"))
            .thenReturn("rejected-hash");

        when(lookup.resolveByHash("rejected-hash"))
            .thenThrow(new InvalidApiKeyException());

        assertThatThrownBy(
            () -> interceptor.preHandle(
                request,
                new MockHttpServletResponse(),
                new Object()
            )
        )
            .isInstanceOf(InvalidApiKeyException.class)
            .hasMessage("Invalid API key");

        verify(metrics).recordRejected();
        verifyNoMoreInteractions(metrics);
    }
}
