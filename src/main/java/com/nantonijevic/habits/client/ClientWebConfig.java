package com.nantonijevic.habits.client;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class ClientWebConfig
    implements WebMvcConfigurer {

    private final ClientAuthenticationInterceptor
        authenticationInterceptor;

    public ClientWebConfig(
        ClientAuthenticationInterceptor
            authenticationInterceptor
    ) {
        this.authenticationInterceptor =
            authenticationInterceptor;
    }

    @Override
    public void addInterceptors(
        InterceptorRegistry registry
    ) {
        registry
            .addInterceptor(authenticationInterceptor)
            .addPathPatterns(
                "/habits",
                "/habits/**"
            );
    }

    @Override
    public void addArgumentResolvers(
        List<HandlerMethodArgumentResolver> resolvers
    ) {
        resolvers.add(
            new ClientTierArgumentResolver()
        );
    }
}
