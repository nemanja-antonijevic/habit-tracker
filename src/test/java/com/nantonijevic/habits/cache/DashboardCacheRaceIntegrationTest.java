package com.nantonijevic.habits.cache;

import com.nantonijevic.habits.config.RedisCacheConfig;
import com.nantonijevic.habits.domain.Habit;
import com.nantonijevic.habits.domain.HabitCompletionStat;
import com.nantonijevic.habits.dto.HabitDashboardResponse;
import com.nantonijevic.habits.event.HabitCompletedEvent;
import com.nantonijevic.habits.event.HabitCompletedEventConsumer;
import com.nantonijevic.habits.repository.HabitCompletionStatRepository;
import com.nantonijevic.habits.repository.HabitWriteRepository;
import com.nantonijevic.habits.service.HabitService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(properties = {
    "spring.kafka.listener.auto-startup=false",
    "spring.cache.type=redis"
})
@Import(
    DashboardCacheRaceIntegrationTest
        .RaceHookConfiguration.class
)
class DashboardCacheRaceIntegrationTest {

    private static final int REDIS_PORT = 6379;

    @Container
    static final GenericContainer<?> redis =
        new GenericContainer<>(
            DockerImageName.parse("redis:7.2.5-alpine")
        ).withExposedPorts(REDIS_PORT);

    @DynamicPropertySource
    static void redisProperties(
        DynamicPropertyRegistry registry
    ) {
        registry.add(
            "spring.data.redis.host",
            redis::getHost
        );
        registry.add(
            "spring.data.redis.port",
            () -> redis.getMappedPort(REDIS_PORT)
        );
    }

    @Autowired
    private HabitService habitService;

    @Autowired
    private HabitWriteRepository habitWriteRepository;

    @Autowired
    private HabitCompletionStatRepository statRepository;

    @Autowired
    private HabitCompletedEventConsumer eventConsumer;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private BlockingStatsReadAspect statsReadHook;

    @BeforeEach
    void clearStateBeforeTest() {
        clearState();
    }

    @AfterEach
    void clearStateAfterTest() {
        clearState();
    }

    // CHARACTERIZATION: proves the existing read-old -> evict -> put-old
    // window. This is NOT the desired behavior: it demonstrates a real
    // stale-cache bug where a reader that captured the old DB snapshot
    // before the after-commit eviction repopulates Redis with that stale
    // value afterwards (bounded only by TTL). When a lock / version token /
    // anti-stampede mechanism is introduced, INVERT these assertions so the
    // test requires that Redis can no longer end up older than the database.
    @Test
    void concurrentReaderCanRepopulateCacheWithStaleSnapshotAfterEviction()
        throws Exception {

        LocalDate today =
            LocalDate.of(2026, 7, 21);

        Habit habit = new Habit("Read");

        habit.setScheduledDays(
            EnumSet.allOf(DayOfWeek.class)
        );

        Habit saved =
            habitWriteRepository.save(habit);

        statRepository.saveAndFlush(
            new HabitCompletionStat(
                saved.getId(),
                today.minusDays(1),
                1,
                1
            )
        );

        String sentinelKey =
            RedisCacheConfig.DASHBOARD_STATS_CACHE
                + "::eviction-sentinel";

        String dashboardKey =
            RedisCacheConfig.DASHBOARD_STATS_CACHE
                + "::"
                + today;

        redisTemplate.opsForValue().set(
            sentinelKey,
            "present"
        );

        CountDownLatch snapshotRead =
            new CountDownLatch(1);

        CountDownLatch releaseReader =
            new CountDownLatch(1);

        statsReadHook.arm(
            snapshotRead,
            releaseReader
        );

        ExecutorService executor =
            Executors.newSingleThreadExecutor();

        try {
            Future<HabitDashboardResponse> reader =
                executor.submit(
                    () ->
                        habitService.getDashboardStats(
                            today
                        )
                );

            boolean snapshotCaptured =
                snapshotRead.await(
                    5,
                    TimeUnit.SECONDS
                );

            if (!snapshotCaptured) {
                reader.get(
                    1,
                    TimeUnit.SECONDS
                );
            }

            assertThat(snapshotCaptured)
                .as(
                    "reader must capture the old "
                        + "database snapshot"
                )
                .isTrue();

            eventConsumer.on(
                new HabitCompletedEvent(
                    saved.getId(),
                    today,
                    2,
                    2
                )
            );

            assertThat(
                redisTemplate.hasKey(sentinelKey)
            )
                .as(
                    "consumer commit must trigger the "
                        + "after-commit cache eviction"
                )
                .isFalse();

            assertThat(
                redisTemplate.hasKey(dashboardKey)
            )
                .as(
                    "dashboard entry must still be "
                        + "absent before the reader resumes"
                )
                .isFalse();

            releaseReader.countDown();

            HabitDashboardResponse readerResult =
                reader.get(
                    5,
                    TimeUnit.SECONDS
                );

            assertThat(
                readerResult.longestActiveStreak()
            )
                .as(
                    "reader must return its captured "
                        + "old snapshot"
                )
                .isEqualTo(1);

            List<HabitCompletionStat> latestStats =
                statRepository.findLatestByHabitIds(
                    List.of(saved.getId())
                );

            assertThat(latestStats)
                .singleElement()
                .satisfies(latest -> {
                    assertThat(
                        latest.getCompletedOn()
                    ).isEqualTo(today);

                    assertThat(
                        latest.getCurrentStreak()
                    ).isEqualTo(2);
                });

            String cachedJson =
                redisTemplate.opsForValue().get(
                    dashboardKey
                );

            assertThat(cachedJson)
                .as(
                    "reader must repopulate Redis "
                        + "with the old snapshot "
                        + "after eviction"
                )
                .isNotNull()
                .contains(
                    "\"longestActiveStreak\":1"
                );
        } finally {
            releaseReader.countDown();

            executor.shutdownNow();

            executor.awaitTermination(
                5,
                TimeUnit.SECONDS
            );
        }
    }

    private void clearState() {
        cacheManager.getCache(
            RedisCacheConfig.DASHBOARD_STATS_CACHE
        ).clear();

        jdbcTemplate.update(
            "DELETE FROM habit_completion_stats"
        );
        jdbcTemplate.update(
            "DELETE FROM habit_completions"
        );
        jdbcTemplate.update(
            "DELETE FROM habits"
        );
    }

    @TestConfiguration
    static class RaceHookConfiguration {

        @Bean
        BlockingStatsReadAspect blockingStatsReadAspect() {
            return new BlockingStatsReadAspect();
        }
    }

    @Aspect
    static class BlockingStatsReadAspect {

        private volatile CountDownLatch snapshotRead =
            new CountDownLatch(0);

        private volatile CountDownLatch releaseReader =
            new CountDownLatch(0);

        void arm(
            CountDownLatch snapshotRead,
            CountDownLatch releaseReader
        ) {
            this.snapshotRead = snapshotRead;
            this.releaseReader = releaseReader;
        }

        @Around(
            "execution(* "
                + "com.nantonijevic.habits.repository."
                + "HabitCompletionStatRepository."
                + "findLatestByHabitIds(..))"
        )
        Object pauseAfterDatabaseRead(
            ProceedingJoinPoint joinPoint
        ) throws Throwable {

            Object oldSnapshot =
                joinPoint.proceed();

            snapshotRead.countDown();

            boolean released =
                releaseReader.await(
                    5,
                    TimeUnit.SECONDS
                );

            if (!released) {
                throw new AssertionError(
                    "Reader was not released in time"
                );
            }

            return oldSnapshot;
        }
    }
}
