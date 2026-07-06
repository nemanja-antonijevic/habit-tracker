package com.nantonijevic.habits.domain;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    void previousScheduledDateBeforeSkipsOffDays() {
        Habit habit = new Habit("Workout");
        habit.setScheduledDays(EnumSet.of(
                DayOfWeek.MONDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.FRIDAY
        ));

        LocalDate monday = LocalDate.of(2026, 7, 6);

        assertThat(habit.previousScheduledDateBefore(monday))
                .isEqualTo(LocalDate.of(2026, 7, 3));
    }

    @Test
    void completeThrowsWhenTodayIsNotScheduled() {
        Habit habit = new Habit("Workout");
        habit.setScheduledDays(EnumSet.of(
                DayOfWeek.MONDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.FRIDAY
        ));

        LocalDate tuesday = LocalDate.of(2026, 7, 7);

        assertThatThrownBy(() ->
                habit.complete(tuesday))
                .isInstanceOf(InvalidHabitStateException.class);
    }

    @Test
    void completeContinuesStreakAcrossOffDays() {
        Habit habit = new Habit("Workout");
        habit.setScheduledDays(EnumSet.of(
                DayOfWeek.MONDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.FRIDAY
        ));

        // Prvi completion u petak
        habit.complete(LocalDate.of(2026, 7, 3));

        // Sledeći zakazani dan je ponedeljak
        habit.complete(LocalDate.of(2026, 7, 6));

        assertThat(habit.getCurrentStreak()).isEqualTo(2);
        assertThat(habit.getLongestStreak()).isEqualTo(2);
    }

    @Test
    void effectiveCurrentStreakStaysAliveAcrossOffDays() {
        Habit habit = new Habit("Workout");
        habit.setScheduledDays(EnumSet.of(
                DayOfWeek.MONDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.FRIDAY
        ));

        habit.complete(LocalDate.of(2026, 7, 3)); // Friday

        int streak = habit.effectiveCurrentStreak(LocalDate.of(2026, 7, 6)); // Monday

        assertThat(streak).isEqualTo(1);
    }

    @Test
    void setScheduledDaysRejectsEmptySchedule() {
        Habit habit = new Habit("Workout");

        assertThatThrownBy(() -> habit.setScheduledDays(EnumSet.noneOf(DayOfWeek.class)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getScheduledDaysReturnsDefensiveCopy() {
        Habit habit = new Habit("Workout");
        EnumSet<DayOfWeek> scheduledDays = habit.getScheduledDays();

        scheduledDays.clear();

        assertThat(habit.getScheduledDays())
                .containsExactlyInAnyOrder(DayOfWeek.values());
    }
}
