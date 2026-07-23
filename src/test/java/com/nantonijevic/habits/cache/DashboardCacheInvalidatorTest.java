package com.nantonijevic.habits.cache;

import com.nantonijevic.habits.config.RedisCacheConfig;
import com.nantonijevic.habits.event.DashboardChangedEvent;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.RedisConnectionFailureException;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DashboardCacheInvalidatorTest {

    @Test
    void redisFailureDoesNotEscapeInvalidator() {
        CacheManager cacheManager =
            mock(CacheManager.class);

        Cache cache =
            mock(Cache.class);

        DashboardCacheGeneration generation =
            mock(DashboardCacheGeneration.class);

        when(cacheManager.getCache(
            RedisCacheConfig.DASHBOARD_STATS_CACHE
        )).thenReturn(cache);

        doThrow(
            new RedisConnectionFailureException(
                "Redis unavailable"
            )
        )
            .when(cache)
            .clear();

        DashboardCacheInvalidator invalidator =
            new DashboardCacheInvalidator(
                cacheManager,
                generation
            );

        assertThatCode(() ->
            invalidator.on(
                new DashboardChangedEvent()
            )
        ).doesNotThrowAnyException();

        InOrder inOrder =
            inOrder(generation, cache);

        inOrder.verify(generation).advance();
        inOrder.verify(cache).clear();
    }

    @Test
    void missingDashboardCacheStillFailsLoudly() {
        CacheManager cacheManager =
            mock(CacheManager.class);

        DashboardCacheGeneration generation =
            mock(DashboardCacheGeneration.class);

        when(cacheManager.getCache(
            RedisCacheConfig.DASHBOARD_STATS_CACHE
        )).thenReturn(null);

        DashboardCacheInvalidator invalidator =
            new DashboardCacheInvalidator(
                cacheManager,
                generation
            );

        assertThatThrownBy(() ->
            invalidator.on(
                new DashboardChangedEvent()
            )
        )
            .isInstanceOf(IllegalStateException.class)
            .hasMessage(
                "Dashboard cache is not configured"
            );

        verifyNoInteractions(generation);
    }

    @Test
    void invalidatorDoesNotSwallowGenerationFailure() {
        CacheManager cacheManager =
            mock(CacheManager.class);

        Cache cache =
            mock(Cache.class);

        DashboardCacheGeneration generation =
            mock(DashboardCacheGeneration.class);

        when(cacheManager.getCache(
            RedisCacheConfig.DASHBOARD_STATS_CACHE
        )).thenReturn(cache);

        doThrow(
            new IllegalStateException(
                "Redis did not return the advanced "
                    + "dashboard cache generation"
            )
        )
            .when(generation)
            .advance();

        DashboardCacheInvalidator invalidator =
            new DashboardCacheInvalidator(
                cacheManager,
                generation
            );

        assertThatThrownBy(() ->
            invalidator.on(
                new DashboardChangedEvent()
            )
        )
            .isInstanceOf(IllegalStateException.class)
            .hasMessage(
                "Redis did not return the advanced "
                    + "dashboard cache generation"
            );

        verifyNoInteractions(cache);
    }

    @Test
    void redisFailureIsLoggedAsWarningWithThrowable() {
        CacheManager cacheManager =
            mock(CacheManager.class);

        Cache cache =
            mock(Cache.class);

        DashboardCacheGeneration generation =
            mock(DashboardCacheGeneration.class);

        when(cacheManager.getCache(
            RedisCacheConfig.DASHBOARD_STATS_CACHE
        )).thenReturn(cache);

        doThrow(
            new RedisConnectionFailureException(
                "Redis unavailable"
            )
        )
            .when(cache)
            .clear();

        DashboardCacheInvalidator invalidator =
            new DashboardCacheInvalidator(
                cacheManager,
                generation
            );

        Logger invalidatorLogger =
            (Logger) LoggerFactory.getLogger(
                DashboardCacheInvalidator.class
            );

        ListAppender<ILoggingEvent> logAppender =
            new ListAppender<>();

        logAppender.start();
        invalidatorLogger.addAppender(logAppender);

        try {
            invalidator.on(
                new DashboardChangedEvent()
            );

            assertThat(logAppender.list)
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.getLevel())
                        .isEqualTo(Level.WARN);

                    assertThat(
                        event.getFormattedMessage()
                    ).isEqualTo(
                        "Dashboard cache eviction failed; "
                            + "stale data may remain until "
                            + "TTL expiry"
                    );

                    assertThat(
                        event.getThrowableProxy()
                    ).isNotNull();
                });
        } finally {
            invalidatorLogger.detachAppender(
                logAppender
            );
            logAppender.stop();
        }
    }
}
