package com.nantonijevic.habits.cache;

import com.nantonijevic.habits.config.RedisCacheConfig;
import com.nantonijevic.habits.event.DashboardChangedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.RedisConnectionFailureException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DashboardCacheInvalidatorTest {

    @Test
    void redisFailureDoesNotEscapeInvalidator() {
        CacheManager cacheManager = mock(CacheManager.class);
        Cache cache = mock(Cache.class);

        when(cacheManager.getCache(
            RedisCacheConfig.DASHBOARD_STATS_CACHE
        )).thenReturn(cache);

        doThrow(new RedisConnectionFailureException("Redis unavailable"))
            .when(cache)
            .clear();

        DashboardCacheInvalidator invalidator =
            new DashboardCacheInvalidator(cacheManager);

        assertThatCode(() ->
            invalidator.on(new DashboardChangedEvent())
        ).doesNotThrowAnyException();

        verify(cache).clear();
    }

    @Test
    void missingDashboardCacheStillFailsLoudly() {
        CacheManager cacheManager = mock(CacheManager.class);

        when(cacheManager.getCache(
            RedisCacheConfig.DASHBOARD_STATS_CACHE
        )).thenReturn(null);

        DashboardCacheInvalidator invalidator =
            new DashboardCacheInvalidator(cacheManager);

        assertThatThrownBy(() ->
            invalidator.on(new DashboardChangedEvent())
        )
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Dashboard cache is not configured");
    }
}
