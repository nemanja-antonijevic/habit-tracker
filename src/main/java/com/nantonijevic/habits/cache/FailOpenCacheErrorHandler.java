package com.nantonijevic.habits.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.dao.DataAccessException;

public class FailOpenCacheErrorHandler
    implements CacheErrorHandler {

    private static final Logger logger =
        LoggerFactory.getLogger(
            FailOpenCacheErrorHandler.class
        );

    @Override
    public void handleCacheGetError(
        RuntimeException exception,
        Cache cache,
        Object key
    ) {
        handleOperationalFailure(
            "GET",
            exception,
            cache,
            key
        );
    }

    @Override
    public void handleCachePutError(
        RuntimeException exception,
        Cache cache,
        Object key,
        Object value
    ) {
        handleOperationalFailure(
            "PUT",
            exception,
            cache,
            key
        );
    }

    @Override
    public void handleCacheEvictError(
        RuntimeException exception,
        Cache cache,
        Object key
    ) {
        throw exception;
    }

    @Override
    public void handleCacheClearError(
        RuntimeException exception,
        Cache cache
    ) {
        throw exception;
    }

    private void handleOperationalFailure(
        String operation,
        RuntimeException exception,
        Cache cache,
        Object key
    ) {
        if (!(exception instanceof DataAccessException)) {
            throw exception;
        }

        logger.warn(
            "Cache {} failed for cache '{}', key '{}'; "
                + "continuing without cache",
            operation,
            cache.getName(),
            key,
            exception
        );
    }
}
