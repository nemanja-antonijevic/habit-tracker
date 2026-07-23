package com.nantonijevic.habits.cache;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class DashboardCacheGeneration {

    // One ':' is deliberate: clear() evicts "dashboard-stats::*"; "::" here would reset the counter.
    static final String GENERATION_KEY =
        "dashboard-stats:generation";

    private final StringRedisTemplate redisTemplate;

    public DashboardCacheGeneration(
        StringRedisTemplate redisTemplate
    ) {
        this.redisTemplate = redisTemplate;
    }

    public long current() {
        String storedGeneration =
            redisTemplate.opsForValue().get(
                GENERATION_KEY
            );

        if (storedGeneration == null) {
            return 0L;
        }

        return Long.parseLong(storedGeneration);
    }

    public long advance() {
        Long advancedGeneration =
            redisTemplate.opsForValue().increment(
                GENERATION_KEY
            );

        if (advancedGeneration == null) {
            throw new IllegalStateException(
                "Redis did not return the advanced "
                    + "dashboard cache generation"
            );
        }

        return advancedGeneration;
    }
}
