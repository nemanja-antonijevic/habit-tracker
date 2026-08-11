package com.nantonijevic.habits.controller;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.nantonijevic.habits.AbstractIntegrationTest;
import com.nantonijevic.habits.cache.DashboardCacheGeneration;
import com.nantonijevic.habits.domain.Habit;
import com.nantonijevic.habits.event.HabitCompletedEvent;
import com.nantonijevic.habits.event.HabitEvent;
import com.nantonijevic.habits.repository.HabitMapper;
import com.nantonijevic.habits.repository.HabitWriteRepository;
import com.nantonijevic.habits.repository.HabitWriteRepositoryImpl;
import com.nantonijevic.habits.service.HabitCommandService;
import com.nantonijevic.habits.support.HabitTestFixtureRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@Testcontainers
@TestPropertySource(
    properties =
        "spring.kafka.listener.auto-startup=false"
)
@Import(
    HabitCompletionConcurrencyMySqlIT
        .FixedClockConfiguration.class
)
@AutoConfigureMockMvc
class HabitCompletionConcurrencyMySqlIT
    extends AbstractIntegrationTest {

    private static final int CONCURRENT_CALLERS = 2;

    private static final long WRITE_GATE_TIMEOUT_SECONDS = 10;

    private static final long REQUEST_TIMEOUT_SECONDS = 20;

    private static final Instant TEST_INSTANT =
        Instant.parse(
            "2026-07-30T00:30:00Z"
        );

    private static final ZoneId TEST_ZONE =
        ZoneId.of(
            "Etc/GMT+12"
        );

    @Container
    static final MySQLContainer<?> MYSQL =
        new MySQLContainer<>(
            "mysql:8.0.36"
        )
            .withDatabaseName("habits")
            .withUsername("habits")
            .withPassword("habits");

    @DynamicPropertySource
    static void configureMySql(
        DynamicPropertyRegistry registry
    ) {
        registry.add(
            "spring.datasource.url",
            MYSQL::getJdbcUrl
        );
        registry.add(
            "spring.datasource.username",
            MYSQL::getUsername
        );
        registry.add(
            "spring.datasource.password",
            MYSQL::getPassword
        );
        registry.add(
            "spring.datasource.driver-class-name",
            MYSQL::getDriverClassName
        );
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private HabitTestFixtureRepository fixtureRepository;

    @Autowired
    private HabitMapper habitMapper;

    @Autowired
    private Clock clock;

    @SpyBean
    private HabitWriteRepository habitWriteRepository;

    @MockBean
    private KafkaTemplate<String, HabitEvent> kafkaTemplate;

    @MockBean
    private DashboardCacheGeneration dashboardCacheGeneration;

    private Logger habitServiceLogger;

    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void attachHabitServiceLogAppender() {
        habitServiceLogger =
            (Logger) LoggerFactory.getLogger(
                HabitCommandService.class
            );

        logAppender = new ListAppender<>();
        logAppender.start();
        habitServiceLogger.addAppender(logAppender);
    }

    @AfterEach
    void cleanDatabaseAndDetachLogAppender() {
        habitServiceLogger.detachAppender(logAppender);
        logAppender.stop();

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

    @Test
    void mysqlContainerRunsWithV12UniqueConstraint() {
        String mysqlVersion =
            jdbcTemplate.queryForObject(
                "SELECT VERSION()",
                String.class
            );

        Integer uniqueConstraintCount =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.TABLE_CONSTRAINTS
                WHERE CONSTRAINT_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'habit_completions'
                  AND CONSTRAINT_NAME =
                      'uq_completions_habit_completed'
                  AND CONSTRAINT_TYPE = 'UNIQUE'
                """,
                Integer.class
            );

        assertThat(mysqlVersion)
            .startsWith("8.0.36");

        assertThat(uniqueConstraintCount)
            .isEqualTo(1);
    }

    @Test
    void concurrentSameDayCompletionsConvergeThroughMySqlConflictRetry()
        throws Exception {

        LocalDate today =
            LocalDate.now(clock);

        Habit habit =
            createHabitScheduledFor(today);

        CountDownLatch requestsReady =
            new CountDownLatch(
                CONCURRENT_CALLERS
            );

        CountDownLatch startRequests =
            new CountDownLatch(1);

        CountDownLatch writesReady =
            new CountDownLatch(
                CONCURRENT_CALLERS
            );

        HabitWriteRepository realWriteRepository =
            new HabitWriteRepositoryImpl(
                habitMapper
            );

        doAnswer(invocation -> {
            writesReady.countDown();

            boolean bothWritesReachedGate =
                writesReady.await(
                    WRITE_GATE_TIMEOUT_SECONDS,
                    TimeUnit.SECONDS
                );

            if (!bothWritesReachedGate) {
                throw new AssertionError(
                    "Both concurrent requests must reach "
                        + "the optimistic write gate"
                );
            }

            Habit candidate =
                invocation.getArgument(0);

            return realWriteRepository.save(candidate);
        })
            .when(habitWriteRepository)
            .save(argThat(
                candidate ->
                    habit.getId().equals(
                        candidate.getId()
                    )
            ));

        ExecutorService executor =
            Executors.newFixedThreadPool(
                CONCURRENT_CALLERS
            );

        Callable<Integer> completeRequest = () -> {
            requestsReady.countDown();

            boolean released =
                startRequests.await(
                    WRITE_GATE_TIMEOUT_SECONDS,
                    TimeUnit.SECONDS
                );

            if (!released) {
                throw new AssertionError(
                    "Concurrent requests were not released"
                );
            }

            return mockMvc.perform(
                    post(
                        "/habits/{id}/complete",
                        habit.getId()
                    )
                )
                .andReturn()
                .getResponse()
                .getStatus();
        };

        try {
            Future<Integer> firstRequest =
                executor.submit(completeRequest);

            Future<Integer> secondRequest =
                executor.submit(completeRequest);

            assertThat(
                requestsReady.await(
                    WRITE_GATE_TIMEOUT_SECONDS,
                    TimeUnit.SECONDS
                )
            )
                .as(
                    "both requests must reach "
                        + "the start gate"
                )
                .isTrue();

            startRequests.countDown();

            int firstStatus =
                awaitResponseStatus(firstRequest);

            int secondStatus =
                awaitResponseStatus(secondRequest);

            assertThat(
                List.of(
                    firstStatus,
                    secondStatus
                )
            )
                .as(
                    "both MySQL concurrent completions "
                        + "must converge to idempotent success"
                )
                .containsOnly(
                    HttpStatus.OK.value()
                );

            Habit persisted =
                habitMapper.findById(
                    habit.getId()
                );

            assertThat(
                persisted.getCompletionCount()
            ).isEqualTo(1);

            assertThat(
                persisted.getCurrentStreak()
            ).isEqualTo(1);

            assertThat(
                persisted.getVersion()
            ).isEqualTo(1L);

            Integer completionRows =
                jdbcTemplate.queryForObject(
                    """
                    SELECT COUNT(*)
                    FROM habit_completions
                    WHERE habit_id = ?
                      AND completed_on = ?
                    """,
                    Integer.class,
                    habit.getId(),
                    today
                );

            assertThat(completionRows)
                .isEqualTo(1);

            assertThat(logAppender.list)
                .filteredOn(
                    event ->
                        event.getFormattedMessage()
                            .startsWith(
                                "Habit completion version conflict; "
                                    + "retrying once"
                            )
                )
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.getLevel())
                        .isEqualTo(Level.INFO);

                    assertThat(
                        event.getFormattedMessage()
                    )
                        .contains(
                            "habitId: " + habit.getId()
                        )
                        .contains(
                            "date: " + today
                        );
                });

            ArgumentCaptor<HabitEvent> eventCaptor =
                ArgumentCaptor.forClass(
                    HabitEvent.class
                );

            verify(kafkaTemplate).send(
                eq("habit-completed"),
                eq(habit.getId().toString()),
                eventCaptor.capture()
            );

            assertThat(eventCaptor.getValue())
                .isInstanceOf(
                    HabitCompletedEvent.class
                );

            verify(
                dashboardCacheGeneration
            ).advance();
        } finally {
            startRequests.countDown();
            executor.shutdownNow();
        }
    }

    private Habit createHabitScheduledFor(
        LocalDate today
    ) {
        Habit habit =
            new Habit(
                "MySQL concurrent completion"
            );

        habit.setScheduledDays(
            EnumSet.of(
                today.getDayOfWeek()
            )
        );

        return fixtureRepository.save(habit);
    }

    private int awaitResponseStatus(
        Future<Integer> response
    ) throws Exception {
        try {
            return response.get(
                REQUEST_TIMEOUT_SECONDS,
                TimeUnit.SECONDS
            );
        } catch (TimeoutException exception) {
            throw new AssertionError(
                "MySQL concurrent completion did not finish "
                    + "within "
                    + REQUEST_TIMEOUT_SECONDS
                    + " seconds; possible InnoDB lock contention",
                exception
            );
        }
    }

    @TestConfiguration(
        proxyBeanMethods = false
    )
    static class FixedClockConfiguration {

        @Bean
        @Primary
        Clock testClock() {
            return Clock.fixed(
                TEST_INSTANT,
                TEST_ZONE
            );
        }
    }
}
