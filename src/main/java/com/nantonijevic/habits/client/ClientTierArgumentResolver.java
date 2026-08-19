package com.nantonijevic.habits.client;

import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.Optional;

public class ClientTierArgumentResolver
    implements HandlerMethodArgumentResolver {

    public static final String API_KEY_HEADER =
        "X-Api-Key";

    private final ClientTierResolver clientTierResolver;

    public ClientTierArgumentResolver(
        ClientTierResolver clientTierResolver
    ) {
        this.clientTierResolver = clientTierResolver;
    }

    @Override
    public boolean supportsParameter(
        MethodParameter parameter
    ) {
        return parameter.getParameterType()
            .equals(ClientTier.class)
            && parameter.hasParameterAnnotation(
            ResolvedClientTier.class
        );
    }

    @Override
    public ClientTier resolveArgument(
        MethodParameter parameter,
        ModelAndViewContainer modelAndViewContainer,
        NativeWebRequest webRequest,
        WebDataBinderFactory binderFactory
    ) {
        return clientTierResolver.resolve(
            Optional.ofNullable(
                webRequest.getHeader(API_KEY_HEADER)
            )
        );
    }
}
