package com.nantonijevic.habits.dto;

import com.nantonijevic.habits.domain.Habit;

import java.time.Instant;

public record HabitResponse(
        Long id,
        String name,
        int completionCount,
        int currentStreak,
        boolean archived,
        Instant createdAt
) {
    public static HabitResponse from(Habit habit) {
        return new HabitResponse(habit.getId(), habit.getName(), habit.getCompletionCount(), habit.getCurrentStreak(), habit.isArchived(), habit.getCreatedAt());
    }
}
