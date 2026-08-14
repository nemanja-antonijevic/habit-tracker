package com.nantonijevic.habits.controller;

import com.nantonijevic.habits.AbstractIntegrationTest;
import com.nantonijevic.habits.cache.DashboardCacheGeneration;
import com.nantonijevic.habits.domain.Habit;
import com.nantonijevic.habits.event.HabitCompletedEvent;
import com.nantonijevic.habits.event.HabitEvent;
import com.nantonijevic.habits.repository.HabitMapper;
import com.nantonijevic.habits.support.HabitTestFixtureRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@TestPropertySource(
    properties =
        "spring.kafka.listener.auto-startup=false"
)
@AutoConfigureMockMvc
class HabitCompletionConcurrencyIntegrationTest
    extends AbstractIntegrationTest {

    private static final Instant FIXED =
        Instant.parse("2026-01-15T00:00:00Z");

    private static final int CONCURRENT_CALLERS = 2;

    private static final long GATE_TIMEOUT_SECONDS = 5;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private HabitTestFixtureRepository fixtureRepository;

    @Autowired
    private HabitMapper habitMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private KafkaTemplate<String, HabitEvent> kafkaTemplate;

    @MockBean
    private DashboardCacheGeneration dashboardCacheGeneration;

    @AfterEach
    void cleanDatabase() {
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
    void concurrentSameDayCompletionsAreAppliedOnce()
        throws Exception {

        LocalDate today = LocalDate.now();

        Habit habit =
            createHabitScheduledFor(today);

        CountDownLatch requestsReady =
            new CountDownLatch(CONCURRENT_CALLERS);

        CountDownLatch startRequests =
            new CountDownLatch(1);

        ExecutorService executor =
            Executors.newFixedThreadPool(CONCURRENT_CALLERS);

        Callable<Integer> completeRequest = () -> {
            requestsReady.countDown();

            boolean released =
                startRequests.await(
                    GATE_TIMEOUT_SECONDS,
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
                    GATE_TIMEOUT_SECONDS,
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
                firstRequest.get(
                    GATE_TIMEOUT_SECONDS,
                    TimeUnit.SECONDS
                );

            int secondStatus =
                secondRequest.get(
                    GATE_TIMEOUT_SECONDS,
                    TimeUnit.SECONDS
                );

            List<Integer> responseStatuses =
                List.of(
                    firstStatus,
                    secondStatus
                );

            assertThat(responseStatuses)
                .as(
                    "both concurrent same-day completions "
                        + "must converge to an idempotent success"
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
                "Concurrent completion",
                FIXED
            );

        habit.setScheduledDays(
            EnumSet.of(
                today.getDayOfWeek()
            )
        );

        return fixtureRepository.save(habit);
    }
}
