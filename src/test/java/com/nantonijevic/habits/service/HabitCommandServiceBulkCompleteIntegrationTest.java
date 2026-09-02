package com.nantonijevic.habits.service;

import com.nantonijevic.habits.AbstractIntegrationTest;
import com.nantonijevic.habits.cache.DashboardCacheGeneration;
import com.nantonijevic.habits.domain.Habit;
import com.nantonijevic.habits.domain.HabitVersionConflictException;
import com.nantonijevic.habits.dto.BulkCompleteResponse;
import com.nantonijevic.habits.event.HabitEvent;
import com.nantonijevic.habits.repository.HabitMapper;
import com.nantonijevic.habits.repository.HabitWriteRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.TestPropertySource;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;

@TestPropertySource(
    properties =
        "spring.kafka.listener.auto-startup=false"
)
@Import(HabitCommandServiceBulkCompleteIntegrationTest.FixedClockConfiguration.class)
class HabitCommandServiceBulkCompleteIntegrationTest
    extends AbstractIntegrationTest {

    private static final Long OWNER_ID = 501L;

    @org.junit.jupiter.api.BeforeEach
    void ensureTestOwnerExists() {
        com.nantonijevic.habits.support.TestApiClientOwner
            .ensureExists(jdbcTemplate, OWNER_ID);
    }

    // UTC+14 makes the business-day boundary differ from common host zones.
    private static final ZoneId TEST_ZONE =
        ZoneId.of("Pacific/Kiritimati");

    private static final Instant TEST_INSTANT =
        Instant.parse("2024-01-04T10:00:00Z");

    @Autowired
    private HabitCommandService habitCommandService;

    @Autowired
    private HabitMapper habitMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Clock clock;

    @MockBean
    private KafkaTemplate<String, HabitEvent> kafkaTemplate;

    @MockBean
    private DashboardCacheGeneration dashboardCacheGeneration;

    @SpyBean
    private HabitWriteRepository habitWriteRepository;

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
    void bulkCompletePersistsMutatedHabitThroughMyBatis() {
        LocalDate today = LocalDate.of(2024, 1, 5);

        // Literals, not the fixture constants: a mutation to TEST_INSTANT or
        // TEST_ZONE must not move the expected value along with it.
        assertThat(clock.instant()).isEqualTo(Instant.parse("2024-01-04T10:00:00Z"));
        assertThat(clock.getZone()).isEqualTo(ZoneId.of("Pacific/Kiritimati"));

        Habit habit = habitCommandService.create(
            OWNER_ID, "Read",
            Set.of(today.getDayOfWeek())
        );

        BulkCompleteResponse response = habitCommandService.bulkComplete(
            OWNER_ID, List.of(habit.getId()),
            today
        );

        Habit persisted = habitMapper.findById(OWNER_ID, habit.getId());

        assertThat(response.completed()).containsExactly(habit.getId());
        assertThat(persisted.getCompletionCount()).isEqualTo(1);
        assertThat(persisted.getCurrentStreak()).isEqualTo(1);
        assertThat(persisted.getLastCompletedAt()).isNotNull();
        assertThat(persisted.getLastCompletedAt())
            .isEqualTo(Instant.parse("2024-01-04T10:00:00Z"));
        assertThat(persisted.getVersion()).isEqualTo(1L);
    }

    @Test
    void bulkCompleteCommitsFirstDuplicateAndSkipsSecond() {
        LocalDate today = LocalDate.of(2024, 1, 5);

        Habit habit = habitCommandService.create(
            OWNER_ID, "Read",
            Set.of(today.getDayOfWeek())
        );

        BulkCompleteResponse response =
            habitCommandService.bulkComplete(
                OWNER_ID, List.of(
                    habit.getId(),
                    habit.getId()
                ),
                today
            );

        Habit persisted =
            habitMapper.findById(OWNER_ID, habit.getId());

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

        assertThat(response.completed())
            .containsExactly(habit.getId());
        assertThat(response.skipped())
            .containsExactly(habit.getId());
        assertThat(response.conflicted()).isEmpty();

        assertThat(persisted.getCompletionCount()).isEqualTo(1);
        assertThat(persisted.getVersion()).isEqualTo(1L);
        assertThat(completionRows).isEqualTo(1);
    }

    @Test
    void exhaustedConflictDoesNotRollBackAnotherCompletedItem() {
        LocalDate today = LocalDate.of(2024, 1, 5);

        Habit conflictedHabit = habitCommandService.create(
            OWNER_ID, "Read",
            Set.of(today.getDayOfWeek())
        );

        Habit completedHabit = habitCommandService.create(
            OWNER_ID, "Exercise",
            Set.of(today.getDayOfWeek())
        );

        doThrow(
            new HabitVersionConflictException(
                conflictedHabit.getId()
            ),
            new HabitVersionConflictException(
                conflictedHabit.getId()
            )
        )
            .when(habitWriteRepository)
            .save(argThat(
                habit ->
                    conflictedHabit.getId()
                        .equals(habit.getId())
            ));

        BulkCompleteResponse response =
            habitCommandService.bulkComplete(
                OWNER_ID, List.of(
                    conflictedHabit.getId(),
                    completedHabit.getId()
                ),
                today
            );

        Habit persistedConflicted =
            habitMapper.findById(OWNER_ID, conflictedHabit.getId());

        Habit persistedCompleted =
            habitMapper.findById(OWNER_ID, completedHabit.getId());

        Integer conflictedCompletionRows =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM habit_completions
                WHERE habit_id = ?
                  AND completed_on = ?
                """,
                Integer.class,
                conflictedHabit.getId(),
                today
            );

        Integer completedCompletionRows =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM habit_completions
                WHERE habit_id = ?
                  AND completed_on = ?
                """,
                Integer.class,
                completedHabit.getId(),
                today
            );

        assertThat(response.conflicted())
            .containsExactly(conflictedHabit.getId());
        assertThat(response.completed())
            .containsExactly(completedHabit.getId());

        assertThat(persistedConflicted.getCompletionCount())
            .isZero();
        assertThat(persistedConflicted.getVersion())
            .isZero();
        assertThat(conflictedCompletionRows).isZero();

        assertThat(persistedCompleted.getCompletionCount())
            .isEqualTo(1);
        assertThat(persistedCompleted.getVersion())
            .isEqualTo(1L);
        assertThat(completedCompletionRows).isEqualTo(1);
    }

    @TestConfiguration
    static class FixedClockConfiguration {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(TEST_INSTANT, TEST_ZONE);
        }
    }
}
