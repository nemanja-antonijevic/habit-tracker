package com.nantonijevic.habits.dto;

import com.nantonijevic.habits.domain.Habit;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;

class HabitResponseTest {

    @Test
    void fromUsesProvidedTodayForCurrentStreak() {
        Habit habit = new Habit("Read");

        habit.setScheduledDays(EnumSet.of(
                DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY
        ));

        habit.complete(LocalDate.of(2026, 7, 3));

        HabitResponse response = HabitResponse.from(
                habit,
                LocalDate.of(2026, 7, 6)
        );

        assertThat(response.currentStreak()).isEqualTo(1);
    }
}
