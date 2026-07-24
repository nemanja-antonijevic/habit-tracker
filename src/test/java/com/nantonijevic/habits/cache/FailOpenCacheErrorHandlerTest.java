package com.nantonijevic.habits.cache;

import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.data.redis.RedisConnectionFailureException;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FailOpenCacheErrorHandlerTest {

    private final FailOpenCacheErrorHandler errorHandler =
        new FailOpenCacheErrorHandler();

    @Test
    void cacheGetDataAccessFailureDoesNotEscape() {
        Cache cache = cache();

        RuntimeException redisFailure =
            new RedisConnectionFailureException(
                "Redis unavailable"
            );

        assertThatCode(() ->
            errorHandler.handleCacheGetError(
                redisFailure,
                cache,
                "7::2026-07-24"
            )
        ).doesNotThrowAnyException();
    }

    @Test
    void cachePutDataAccessFailureDoesNotEscape() {
        Cache cache = cache();

        RuntimeException redisFailure =
            new RedisConnectionFailureException(
                "Redis unavailable"
            );

        assertThatCode(() ->
            errorHandler.handleCachePutError(
                redisFailure,
                cache,
                "7::2026-07-24",
                "dashboard-result"
            )
        ).doesNotThrowAnyException();
    }

    @Test
    void structuralCacheFailureStillEscapes() {
        Cache cache = cache();

        IllegalStateException structuralFailure =
            new IllegalStateException(
                "Broken cache configuration"
            );

        assertThatThrownBy(() ->
            errorHandler.handleCacheGetError(
                structuralFailure,
                cache,
                "7::2026-07-24"
            )
        ).isSameAs(structuralFailure);
    }

    private Cache cache() {
        Cache cache = mock(Cache.class);

        when(cache.getName())
            .thenReturn("dashboard-stats");

        return cache;
    }

    @Test
    void cacheGetDataAccessFailureIsLoggedAsWarning() {
        Cache cache = cache();

        RedisConnectionFailureException redisFailure =
            new RedisConnectionFailureException(
                "Redis unavailable"
            );

        Logger logger =
            (Logger) LoggerFactory.getLogger(
                FailOpenCacheErrorHandler.class
            );

        ListAppender<ILoggingEvent> logAppender =
            new ListAppender<>();

        logAppender.start();
        logger.addAppender(logAppender);

        try {
            errorHandler.handleCacheGetError(
                redisFailure,
                cache,
                "7::2026-07-24"
            );

            assertThat(logAppender.list).hasSize(1);

            ILoggingEvent event =
                logAppender.list.getFirst();

            assertThat(event.getLevel())
                .isEqualTo(Level.WARN);

            assertThat(event.getFormattedMessage())
                .isEqualTo(
                    "Cache GET failed for cache "
                        + "'dashboard-stats', key "
                        + "'7::2026-07-24'; "
                        + "continuing without cache"
                );

            assertThat(event.getThrowableProxy())
                .isNotNull();
        } finally {
            logger.detachAppender(logAppender);
            logAppender.stop();
        }
    }

    @Test
    void cacheEvictFailureStillEscapes() {
        Cache cache = cache();

        RedisConnectionFailureException redisFailure =
            new RedisConnectionFailureException(
                "Redis unavailable"
            );

        assertThatThrownBy(() ->
            errorHandler.handleCacheEvictError(
                redisFailure,
                cache,
                "7::2026-07-24"
            )
        ).isSameAs(redisFailure);
    }

    @Test
    void cacheClearFailureStillEscapes() {
        Cache cache = cache();

        RedisConnectionFailureException redisFailure =
            new RedisConnectionFailureException(
                "Redis unavailable"
            );

        assertThatThrownBy(() ->
            errorHandler.handleCacheClearError(
                redisFailure,
                cache
            )
        ).isSameAs(redisFailure);
    }
}
