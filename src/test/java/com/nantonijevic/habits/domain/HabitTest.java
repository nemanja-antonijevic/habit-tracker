package com.nantonijevic.habits.domain;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class HabitTest {

    private static final ZoneId TEST_ZONE =
        ZoneId.of("UTC");

    @Test
    void reComplete_sameDay_afterUncomplete_restoresStreak() {
        Habit habit = new Habit("Read 30 min");
        LocalDate today = LocalDate.now(TEST_ZONE);

        habit.complete(
            today.minusDays(2),
            TEST_ZONE
        );
        habit.complete(
            today.minusDays(1),
            TEST_ZONE
        );
        habit.complete(
            today,
            TEST_ZONE
        );

        habit.decrementCompletionCount(
            today,
            List.of(
                today.minusDays(1),
                today.minusDays(2)
            ),
            TEST_ZONE
        );

        habit.complete(
            today,
            TEST_ZONE
        );

        assertThat(habit.getCurrentStreak())
            .isEqualTo(3);
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
                habit.complete(tuesday, TEST_ZONE))
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
        habit.complete(LocalDate.of(2026, 7, 3), TEST_ZONE);

        // Sledeći zakazani dan je ponedeljak
        habit.complete(LocalDate.of(2026, 7, 6), TEST_ZONE);

        assertThat(habit.getCurrentStreak()).isEqualTo(2);
        assertThat(habit.getLongestStreak()).isEqualTo(2);
    }

    @Test
    void uncompleteLastCompletionRecomputesLongestStreakWhenLatestCompletionCreatedNewRecord() {
        Habit habit = new Habit("Read 30 min");

        LocalDate day1 = LocalDate.of(2026, 7, 1);
        LocalDate day2 = LocalDate.of(2026, 7, 2);
        LocalDate day3 = LocalDate.of(2026, 7, 3);

        habit.complete(day1, TEST_ZONE);
        habit.complete(day2, TEST_ZONE);
        habit.complete(day3, TEST_ZONE);

        habit.decrementCompletionCount(
            day3,
            List.of(day2, day1),
            TEST_ZONE
        );

        assertThat(habit.getCurrentStreak())
            .isEqualTo(2);
        assertThat(habit.getLongestStreak())
            .isEqualTo(2);
    }

    @Test
    void uncompleteLastCompletionSetsCurrentStreakToZeroWhenRemainingHistoryIsNotAlive() {
        Habit habit = new Habit("Read 30 min");

        LocalDate day1 = LocalDate.of(2026, 7, 1);
        LocalDate day2 = LocalDate.of(2026, 7, 2);
        LocalDate day5 = LocalDate.of(2026, 7, 5);

        habit.complete(day1, TEST_ZONE);
        habit.complete(day2, TEST_ZONE);
        habit.complete(day5, TEST_ZONE);

        habit.decrementCompletionCount(
            day5,
            List.of(day2, day1),
            TEST_ZONE
        );

        assertThat(habit.getCompletionCount())
            .isEqualTo(2);
        assertThat(habit.getCurrentStreak())
            .isZero();
        assertThat(habit.getLongestStreak())
            .isEqualTo(2);
    }

    @Test
    void effectiveCurrentStreakStaysAliveAcrossOffDays() {
        Habit habit = new Habit("Workout");
        habit.setScheduledDays(EnumSet.of(
                DayOfWeek.MONDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.FRIDAY
        ));

        habit.complete(LocalDate.of(2026, 7, 3), TEST_ZONE); // Friday

        int streak = habit.effectiveCurrentStreak(LocalDate.of(2026, 7, 6), TEST_ZONE); // Monday

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
