package com.nantonijevic.habits.cache;

import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.stereotype.Component;
import org.springframework.dao.DataAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.lang.reflect.Method;
import java.time.LocalDate;

@Component
public class DashboardCacheKeyGenerator implements KeyGenerator {

    private static final Logger logger =
        LoggerFactory.getLogger(
            DashboardCacheKeyGenerator.class
        );

    private final DashboardCacheGeneration generation;

    public DashboardCacheKeyGenerator(
        DashboardCacheGeneration generation
    ) {
        this.generation = generation;
    }

    @Override
    public Object generate(
        Object target,
        Method method,
        Object... params
    ) {
        LocalDate today = (LocalDate) params[0];

        try {
            return generation.current()
                + "::"
                + today;
        } catch (DataAccessException exception) {
            String bypassKey =
                "bypass::"
                    + UUID.randomUUID()
                    + "::"
                    + today;

            logger.warn(
                "Dashboard cache generation read failed; "
                    + "using one-shot cache key",
                exception
            );

            return bypassKey;
        }
    }
}
