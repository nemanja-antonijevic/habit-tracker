package com.nantonijevic.habits.cache;

import com.nantonijevic.habits.config.RedisCacheConfig;
import com.nantonijevic.habits.domain.Habit;
import com.nantonijevic.habits.event.DashboardChangedEvent;
import com.nantonijevic.habits.repository.HabitMapper;
import com.nantonijevic.habits.repository.HabitWriteRepository;
import com.nantonijevic.habits.service.HabitService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(properties = {
    "spring.kafka.listener.auto-startup=false",
    "spring.cache.type=redis"
})
class HabitDashboardCacheIntegrationTest {

    private static final int REDIS_PORT = 6379;

    @Container
    static final GenericContainer<?> redis =
        new GenericContainer<>(
            DockerImageName.parse("redis:7.2.5-alpine")
        ).withExposedPorts(REDIS_PORT);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add(
            "spring.data.redis.port",
            () -> redis.getMappedPort(REDIS_PORT)
        );
    }

    @Autowired
    private HabitService habitService;

    @MockBean
    private HabitMapper habitMapper;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ApplicationEventPublisher applicationEventPublisher;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockBean
    private HabitWriteRepository habitWriteRepository;

    @BeforeEach
    void clearDashboardCache() {
        cacheManager.getCache(
            RedisCacheConfig.DASHBOARD_STATS_CACHE
        ).clear();
    }

    @Test
    void secondDashboardRequestIsCacheHitAndDoesNotRepeatDatabaseRead() {
        LocalDate today = LocalDate.of(2026, 7, 20);

        when(habitMapper.findActive())
            .thenReturn(List.of());

        habitService.getDashboardStats(today);
        habitService.getDashboardStats(today);

        verify(habitMapper, times(1)).findActive();
    }

    @Test
    void differentDatesUseDifferentCacheEntries() {
        LocalDate firstDate = LocalDate.of(2026, 7, 20);
        LocalDate secondDate = firstDate.plusDays(1);

        when(habitMapper.findActive())
            .thenReturn(List.of());

        habitService.getDashboardStats(firstDate);
        habitService.getDashboardStats(secondDate);

        verify(habitMapper, times(2)).findActive();
    }

    @Test
    void dashboardCacheUsesExpectedKeyJsonValueAndFiveMinuteTtl() {
        LocalDate today = LocalDate.of(2026, 7, 20);

        when(habitMapper.findActive())
            .thenReturn(List.of());

        habitService.getDashboardStats(today);

        String key =
            RedisCacheConfig.DASHBOARD_STATS_CACHE
                + "::"
                + today;

        String cachedJson =
            redisTemplate.opsForValue().get(key);

        Long ttlSeconds =
            redisTemplate.getExpire(
                key,
                TimeUnit.SECONDS
            );

        assertThat(cachedJson)
            .isNotNull()
            .contains("\"dueToday\":0")
            .contains("\"completedToday\":0")
            .contains("\"totalHabits\":0");

        assertThat(ttlSeconds)
            .isBetween(1L, 300L);
    }

    @Test
    void dashboardCacheIsEvictedOnlyAfterTransactionCommits() {
        LocalDate today = LocalDate.of(2026, 7, 20);

        when(habitMapper.findActive())
            .thenReturn(List.of());

        habitService.getDashboardStats(today);

        String key =
            RedisCacheConfig.DASHBOARD_STATS_CACHE
                + "::"
                + today;

        assertThat(redisTemplate.hasKey(key)).isTrue();

        TransactionTemplate transactionTemplate =
            new TransactionTemplate(transactionManager);

        transactionTemplate.executeWithoutResult(status -> {
            applicationEventPublisher.publishEvent(
                new DashboardChangedEvent()
            );

            assertThat(redisTemplate.hasKey(key))
                .as("cache must remain populated before commit")
                .isTrue();
        });

        assertThat(redisTemplate.hasKey(key))
            .as("cache must be evicted after commit")
            .isFalse();
    }

    @Test
    void createEvictsDashboardCacheAfterTransactionCommits() {
        LocalDate today = LocalDate.of(2026, 7, 20);

        when(habitMapper.findActive())
            .thenReturn(List.of());

        habitService.getDashboardStats(today);

        String key =
            RedisCacheConfig.DASHBOARD_STATS_CACHE
                + "::"
                + today;

        assertThat(redisTemplate.hasKey(key)).isTrue();

        when(habitWriteRepository.save(
            any(Habit.class)
        )).thenAnswer(invocation -> invocation.getArgument(0));

        habitService.create(
            "Read",
            EnumSet.allOf(DayOfWeek.class)
        );

        assertThat(redisTemplate.hasKey(key))
            .as("create must evict dashboard cache after commit")
            .isFalse();
    }
}
