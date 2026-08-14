package com.nantonijevic.habits.service;

import com.nantonijevic.habits.AbstractIntegrationTest;
import com.nantonijevic.habits.domain.Habit;
import com.nantonijevic.habits.domain.HabitCompletionStat;
import com.nantonijevic.habits.dto.HabitStatsView;
import com.nantonijevic.habits.repository.HabitCompletionStatRepository;
import com.nantonijevic.habits.repository.HabitWriteRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class HabitQueryServiceStatsIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private HabitQueryService habitQueryService;

    @Autowired
    private HabitWriteRepository habitWriteRepository;

    @Autowired
    private HabitCompletionStatRepository completionStatRepository;

    @Test
    void getStatsProjectionUsesMappedScheduleAcrossOffDays() {
        LocalDate today = LocalDate.of(2024, 1, 5);
        LocalDate lastCompleted = LocalDate.of(2024, 1, 3);

        Habit habit = new Habit(
            "Workout",
            Instant.parse("2023-12-31T00:00:00Z")
        );
        habit.setScheduledDays(
            EnumSet.of(
                DayOfWeek.MONDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.FRIDAY
            )
        );

        Habit saved = habitWriteRepository.save(habit);

        completionStatRepository.save(
            new HabitCompletionStat(
                saved.getId(),
                lastCompleted,
                4,
                4
            )
        );

        HabitStatsView result = habitQueryService.getStatsProjection(
            saved.getId(),
            today
        );

        assertThat(result.currentStreak()).isEqualTo(4);
    }
}
