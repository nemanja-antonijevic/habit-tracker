package com.nantonijevic.habits.habit;

import java.time.Instant;

public record HabitResponse(
        Long id,
        String name,
        int completionCount,
        int currentStreak,
        Instant createdAt
) {
    public static HabitResponse from(Habit habit) {
        return new HabitResponse(habit.getId(), habit.getName(), habit.getCompletionCount(), habit.getCurrentStreak(), habit.getCreatedAt());
    }
}
