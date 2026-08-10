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
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.TestPropertySource;

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
class HabitCommandServiceBulkCompleteIntegrationTest
    extends AbstractIntegrationTest {

    @Autowired
    private HabitCommandService habitCommandService;

    @Autowired
    private HabitMapper habitMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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

        Habit habit = habitCommandService.create(
            "Read",
            Set.of(today.getDayOfWeek())
        );

        BulkCompleteResponse response = habitCommandService.bulkComplete(
            List.of(habit.getId()),
            today
        );

        Habit persisted = habitMapper.findById(habit.getId());

        assertThat(response.completed()).containsExactly(habit.getId());
        assertThat(persisted.getCompletionCount()).isEqualTo(1);
        assertThat(persisted.getCurrentStreak()).isEqualTo(1);
        assertThat(persisted.getLastCompletedAt()).isNotNull();
        assertThat(LocalDate.ofInstant(
            persisted.getLastCompletedAt(),
            ZoneId.systemDefault()
        )).isEqualTo(today);
        assertThat(persisted.getVersion()).isEqualTo(1L);
    }

    @Test
    void bulkCompleteCommitsFirstDuplicateAndSkipsSecond() {
        LocalDate today = LocalDate.of(2024, 1, 5);

        Habit habit = habitCommandService.create(
            "Read",
            Set.of(today.getDayOfWeek())
        );

        BulkCompleteResponse response =
            habitCommandService.bulkComplete(
                List.of(
                    habit.getId(),
                    habit.getId()
                ),
                today
            );

        Habit persisted =
            habitMapper.findById(habit.getId());

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
            "Read",
            Set.of(today.getDayOfWeek())
        );

        Habit completedHabit = habitCommandService.create(
            "Exercise",
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
                List.of(
                    conflictedHabit.getId(),
                    completedHabit.getId()
                ),
                today
            );

        Habit persistedConflicted =
            habitMapper.findById(conflictedHabit.getId());

        Habit persistedCompleted =
            habitMapper.findById(completedHabit.getId());

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
}
