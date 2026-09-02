package com.nantonijevic.habits.client;

import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

public class ClientTierArgumentResolver
    implements HandlerMethodArgumentResolver {

    public static final String API_KEY_HEADER =
        "X-Api-Key";

    @Override
    public boolean supportsParameter(
        MethodParameter parameter
    ) {
        Class<?> parameterType = parameter.getParameterType();

        return (
            parameterType.equals(ClientContext.class)
                || parameterType.equals(ClientTier.class)
        ) && parameter.hasParameterAnnotation(
            ResolvedClientTier.class
        );
    }

    @Override
    public Object resolveArgument(
        MethodParameter parameter,
        ModelAndViewContainer modelAndViewContainer,
        NativeWebRequest webRequest,
        WebDataBinderFactory binderFactory
    ) {
        Object attribute = webRequest.getAttribute(
            ClientAuthenticationInterceptor
                .CLIENT_CONTEXT_ATTRIBUTE,
            RequestAttributes.SCOPE_REQUEST
        );

        if (!(attribute instanceof ClientContext context)) {
            throw new InvalidApiKeyException();
        }

        return parameter.getParameterType()
            .equals(ClientContext.class)
                ? context
                : context.tier();
    }
}
