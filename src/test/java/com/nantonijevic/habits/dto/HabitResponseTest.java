package com.nantonijevic.habits.dto;

import com.nantonijevic.habits.domain.Habit;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;

class HabitResponseTest {

    private static final Long OWNER_ID = 101L;

    private static final ZoneId TEST_ZONE =
        ZoneId.of("UTC");

    @Test
    void fromUsesProvidedTodayForCurrentStreak() {
        Habit habit = new Habit(OWNER_ID, "Read", Instant.EPOCH);

        habit.setScheduledDays(EnumSet.of(
            DayOfWeek.MONDAY,
            DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY,
            DayOfWeek.FRIDAY
        ));

        habit.complete(
            LocalDate.of(2026, 7, 3),
            TEST_ZONE
        );

        HabitResponse response = HabitResponse.from(
            habit,
            LocalDate.of(2026, 7, 6),
            TEST_ZONE
        );

        assertThat(response.currentStreak())
            .isEqualTo(1);
    }
}
