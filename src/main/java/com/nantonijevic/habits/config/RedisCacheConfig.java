package com.nantonijevic.habits.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

@Configuration
@EnableCaching
public class RedisCacheConfig {

    public static final String DASHBOARD_STATS_CACHE =
        "dashboard-stats";

    private static final Duration DASHBOARD_STATS_TTL =
        Duration.ofMinutes(5);

    @Bean
    @ConditionalOnProperty(
        name = "spring.cache.type",
        havingValue = "redis",
        matchIfMissing = true
    )
    public RedisCacheManager redisCacheManager(
        RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration dashboardConfiguration =
            RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(DASHBOARD_STATS_TTL)
                .serializeKeysWith(
                    RedisSerializationContext.SerializationPair
                        .fromSerializer(
                            new StringRedisSerializer()
                        )
                )
                .serializeValuesWith(
                    RedisSerializationContext.SerializationPair
                        .fromSerializer(
                            new GenericJackson2JsonRedisSerializer()
                        )
                )
                .disableCachingNullValues();

        return RedisCacheManager.builder(connectionFactory)
            .withCacheConfiguration(
                DASHBOARD_STATS_CACHE,
                dashboardConfiguration
            )
            .build();
    }
}
