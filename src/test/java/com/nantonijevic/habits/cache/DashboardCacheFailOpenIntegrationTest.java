package com.nantonijevic.habits.cache;

import com.nantonijevic.habits.config.RedisCacheConfig;
import com.nantonijevic.habits.domain.Habit;
import com.nantonijevic.habits.dto.HabitDashboardResponse;
import com.nantonijevic.habits.repository.HabitMapper;
import com.nantonijevic.habits.service.HabitService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

@SpringBootTest(properties = {
    "spring.kafka.listener.auto-startup=false",
    "spring.cache.type=redis"
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

    @MockBean
    private DashboardCacheGeneration dashboardCacheGeneration;

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

        verify(dashboardCacheGeneration).advance();
        verify(cache).clear();
    }

    @Test
    void dashboardReadFallsBackToDatabaseWhenCacheGetFails() {
        LocalDate today =
            LocalDate.of(2026, 7, 24);

        long generation = 7L;

        String key =
            generation
                + "::"
                + today;

        when(
            dashboardCacheGeneration.current()
        ).thenReturn(generation);

        when(cache.get(key))
            .thenThrow(
                new RedisConnectionFailureException(
                    "Redis unavailable"
                )
            );

        HabitDashboardResponse response =
            habitService.getDashboardStats(today);

        assertThat(response).isNotNull();
        assertThat(response.totalHabits()).isZero();

        verify(cache).get(key);
        verify(cache).put(key, response);
    }

    @Test
    void dashboardReadReturnsDatabaseResultWhenCachePutFails() {
        LocalDate today =
            LocalDate.of(2026, 7, 24);

        long generation = 7L;

        String key =
            generation
                + "::"
                + today;

        when(
            dashboardCacheGeneration.current()
        ).thenReturn(generation);

        when(cache.get(key))
            .thenReturn(null);

        doThrow(
            new RedisConnectionFailureException(
                "Redis unavailable"
            )
        )
            .when(cache)
            .put(
                any(),
                any()
            );

        HabitDashboardResponse response =
            habitService.getDashboardStats(today);

        assertThat(response).isNotNull();
        assertThat(response.totalHabits()).isZero();

        verify(cache).get(key);
        verify(cache).put(key, response);
    }

    @Test
    void dashboardReadFallsBackToDatabaseWhenGenerationReadFails() {
        LocalDate today =
            LocalDate.of(2026, 7, 24);

        when(
            dashboardCacheGeneration.current()
        ).thenThrow(
            new RedisConnectionFailureException(
                "Redis unavailable"
            )
        );

        HabitDashboardResponse response =
            habitService.getDashboardStats(today);

        assertThat(response).isNotNull();
        assertThat(response.totalHabits()).isZero();

        ArgumentCaptor<Object> keyCaptor =
            ArgumentCaptor.forClass(Object.class);

        verify(cache).get(keyCaptor.capture());

        Object generatedKey =
            keyCaptor.getValue();

        assertThat(generatedKey.toString())
            .startsWith("bypass::")
            .endsWith("::2026-07-24");

        verify(cache).put(
            generatedKey,
            response
        );
    }
}
