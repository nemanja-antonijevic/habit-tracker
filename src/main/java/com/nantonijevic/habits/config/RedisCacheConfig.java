package com.nantonijevic.habits.config;

import com.nantonijevic.habits.cache.FailOpenCacheErrorHandler;
import com.nantonijevic.habits.client.ClientTier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

@Configuration
@EnableCaching
public class RedisCacheConfig implements CachingConfigurer {

    public static final String DASHBOARD_STATS_CACHE =
        "dashboard-stats";

    public static final String API_CLIENT_TIERS_CACHE =
        "api-client-tiers";

    private static final Duration DASHBOARD_STATS_TTL =
        Duration.ofMinutes(5);

    // Tier changes, especially revocation, must propagate
    // faster than derived dashboard data.
    private static final Duration API_CLIENT_TIERS_TTL =
        Duration.ofMinutes(1);

    @Bean
    @ConditionalOnProperty(
        name = "spring.cache.type",
        havingValue = "redis",
        matchIfMissing = true
    )
    public RedisCacheManager redisCacheManager(
        RedisConnectionFactory connectionFactory
    ) {
        RedisCacheConfiguration baseConfiguration =
            RedisCacheConfiguration.defaultCacheConfig()
                .serializeKeysWith(
                    RedisSerializationContext.SerializationPair
                        .fromSerializer(
                            new StringRedisSerializer()
                        )
                )
                .disableCachingNullValues();

        RedisCacheConfiguration dashboardConfiguration =
            baseConfiguration
                .entryTtl(DASHBOARD_STATS_TTL)
                .serializeValuesWith(
                    RedisSerializationContext.SerializationPair
                        .fromSerializer(
                            new GenericJackson2JsonRedisSerializer()
                        )
                );

        Jackson2JsonRedisSerializer<ClientTier> clientTierSerializer =
            new Jackson2JsonRedisSerializer<>(ClientTier.class);

        RedisCacheConfiguration apiClientTierConfiguration =
            baseConfiguration
                .entryTtl(API_CLIENT_TIERS_TTL)
                .serializeValuesWith(
                    RedisSerializationContext.SerializationPair
                        .fromSerializer(clientTierSerializer)
                );

        return RedisCacheManager
            .builder(connectionFactory)
            .enableStatistics()
            .withCacheConfiguration(
                DASHBOARD_STATS_CACHE,
                dashboardConfiguration
            )
            .withCacheConfiguration(
                API_CLIENT_TIERS_CACHE,
                apiClientTierConfiguration
            )
            .build();
    }

    @Override
    public CacheErrorHandler errorHandler() {
        return new FailOpenCacheErrorHandler();
    }
}
