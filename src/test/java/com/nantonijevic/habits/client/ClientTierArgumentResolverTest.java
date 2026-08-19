package com.nantonijevic.habits.client;

import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ClientTierArgumentResolverTest {

    private final ClientTierArgumentResolver resolver =
        new ClientTierArgumentResolver(
            mock(ClientTierResolver.class)
        );

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
