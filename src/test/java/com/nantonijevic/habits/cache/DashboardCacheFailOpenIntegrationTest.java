package com.nantonijevic.habits.cache;

import com.nantonijevic.habits.config.RedisCacheConfig;
import com.nantonijevic.habits.domain.Habit;
import com.nantonijevic.habits.repository.HabitMapper;
import com.nantonijevic.habits.service.HabitService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.DayOfWeek;
import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
    "spring.kafka.listener.auto-startup=false"
})
class DashboardCacheFailOpenIntegrationTest {

    @Autowired
    private HabitService habitService;

    @Autowired
    private HabitMapper habitMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private CacheManager cacheManager;

    private Cache cache;

    @BeforeEach
    void simulateUnavailableRedis() {
        cache = mock(Cache.class);

        when(cacheManager.getCache(
            RedisCacheConfig.DASHBOARD_STATS_CACHE
        )).thenReturn(cache);

        doThrow(new RedisConnectionFailureException("Redis unavailable"))
            .when(cache)
            .clear();
    }

    @AfterEach
    void cleanCommittedHabit() {
        jdbcTemplate.update("DELETE FROM habits");
    }

    @Test
    void createCommitsWhenDashboardCacheEvictionFails() {
        Habit created = habitService.create(
            "Read",
            EnumSet.allOf(DayOfWeek.class)
        );

        assertThat(created.getId()).isNotNull();
        assertThat(habitMapper.findById(created.getId())).isNotNull();

        verify(cache).clear();
    }
}
