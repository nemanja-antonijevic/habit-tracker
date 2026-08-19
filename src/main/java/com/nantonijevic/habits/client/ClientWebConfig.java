package com.nantonijevic.habits.client;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class ClientWebConfig
    implements WebMvcConfigurer {

    private final ClientTierResolver clientTierResolver;

    public ClientWebConfig(
        ClientTierResolver clientTierResolver
    ) {
        this.clientTierResolver = clientTierResolver;
    }

    @Override
    public void addArgumentResolvers(
        List<HandlerMethodArgumentResolver> resolvers
    ) {
        resolvers.add(
            new ClientTierArgumentResolver(
                clientTierResolver
            )
        );
    }
}
