package com.nantonijevic.habits.client;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class ClientAuthenticationInterceptor
    implements HandlerInterceptor {

    public static final String CLIENT_CONTEXT_ATTRIBUTE =
        ClientAuthenticationInterceptor.class.getName()
            + ".clientContext";

    private final ApiKeyHasher hasher;
    private final ClientIdentityLookup lookup;
    private final ClientTierResolutionMetrics metrics;

    public ClientAuthenticationInterceptor(
        ApiKeyHasher hasher,
        ClientIdentityLookup lookup,
        ClientTierResolutionMetrics metrics
    ) {
        this.hasher = hasher;
        this.lookup = lookup;
        this.metrics = metrics;
    }

    @Override
    public boolean preHandle(
        HttpServletRequest request,
        HttpServletResponse response,
        Object handler
    ) {
        String apiKey = request.getHeader(
            ClientTierArgumentResolver.API_KEY_HEADER
        );

        if (apiKey == null) {
            metrics.recordRejected();

            throw new InvalidApiKeyException();
        }

        try {
            ClientContext context = lookup.resolveByHash(
                hasher.hash(apiKey)
            );

            request.setAttribute(
                CLIENT_CONTEXT_ATTRIBUTE,
                context
            );

            metrics.recordResolved();

            return true;
        } catch (InvalidApiKeyException exception) {
            metrics.recordRejected();

            throw exception;
        }
    }
}
