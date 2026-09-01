package com.nantonijevic.habits.client;

import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClientTierArgumentResolverTest {

    private final ClientTierArgumentResolver resolver =
        new ClientTierArgumentResolver();

    @Test
    void supportsAnnotatedClientTierParameter()
        throws Exception {

        assertThat(
            resolver.supportsParameter(
                parameterFor(
                    "annotatedClientTier",
                    ClientTier.class
                )
            )
        ).isTrue();
    }

    @Test
    void doesNotClaimUnannotatedClientTierParameter()
        throws Exception {

        assertThat(
            resolver.supportsParameter(
                parameterFor(
                    "unannotatedClientTier",
                    ClientTier.class
                )
            )
        ).isFalse();
    }

    @Test
    void doesNotClaimAnnotatedParameterOfAnotherType()
        throws Exception {

        assertThat(
            resolver.supportsParameter(
                parameterFor(
                    "annotatedString",
                    String.class
                )
            )
        ).isFalse();
    }

    @Test
    void returnsTierFromAuthenticatedClientContext() {
        MockHttpServletRequest request =
            new MockHttpServletRequest();

        request.setAttribute(
            ClientAuthenticationInterceptor
                .CLIENT_CONTEXT_ATTRIBUTE,
            new ClientContext(
                42L,
                ClientTier.INTERNAL
            )
        );

        ClientTier tier = resolver.resolveArgument(
            null,
            null,
            new ServletWebRequest(request),
            null
        );

        assertThat(tier)
            .isEqualTo(ClientTier.INTERNAL);
    }

    @Test
    void rejectsMissingClientContext() {
        MockHttpServletRequest request =
            new MockHttpServletRequest();

        assertThatThrownBy(
            () -> resolver.resolveArgument(
                null,
                null,
                new ServletWebRequest(request),
                null
            )
        )
            .isInstanceOf(InvalidApiKeyException.class)
            .hasMessage("Invalid API key");
    }

    private MethodParameter parameterFor(
        String methodName,
        Class<?> parameterType
    ) throws NoSuchMethodException {
        Method method = Fixture.class.getDeclaredMethod(
            methodName,
            parameterType
        );

        return new MethodParameter(method, 0);
    }

    private static class Fixture {

        void annotatedClientTier(
            @ResolvedClientTier ClientTier tier
        ) {
        }

        void unannotatedClientTier(ClientTier tier) {
        }

        void annotatedString(
            @ResolvedClientTier String value
        ) {
        }
    }
}
