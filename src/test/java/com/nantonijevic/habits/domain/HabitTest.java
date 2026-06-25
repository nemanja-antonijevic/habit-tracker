package com.nantonijevic.habits.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

public class HabitTest {

    @Test
    void reComplete_sameDay_afterUncomplete_restoresStreak() {
        var habit = new Habit("Read 30 min");
        LocalDate today = LocalDate.now();

        habit.complete(today.minusDays(2));
        habit.complete(today.minusDays(1));
        habit.complete(today);
        habit.decrementCompletionCount(today, today.minusDays(1));
        habit.complete(today);

        assertThat(habit.getCurrentStreak()).isEqualTo(3);
    }
}
