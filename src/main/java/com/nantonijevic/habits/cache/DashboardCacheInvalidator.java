package com.nantonijevic.habits.cache;

import com.nantonijevic.habits.config.RedisCacheConfig;
import com.nantonijevic.habits.event.DashboardChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class DashboardCacheInvalidator {

    private static final Logger logger =
        LoggerFactory.getLogger(DashboardCacheInvalidator.class);

    private final CacheManager cacheManager;

    public DashboardCacheInvalidator(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    @TransactionalEventListener(
        phase = TransactionPhase.AFTER_COMMIT
    )
    public void on(DashboardChangedEvent event) {
        Cache cache = cacheManager.getCache(
            RedisCacheConfig.DASHBOARD_STATS_CACHE
        );

        if (cache == null) {
            throw new IllegalStateException(
                "Dashboard cache is not configured"
            );
        }

        try {
            cache.clear();
        } catch (DataAccessException ex) {
            logger.warn(
                "Dashboard cache eviction failed; stale data may remain until TTL expiry",
                ex
            );
        }
    }
}
