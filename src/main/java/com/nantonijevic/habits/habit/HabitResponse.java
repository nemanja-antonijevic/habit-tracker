package com.nantonijevic.habits.habit;

import java.time.Instant;

public record HabitResponse(
        Long id,
        String name,
        Instant createdAt
) {
    public static HabitResponse from(Habit habit) {
        return new HabitResponse(habit.getId(), habit.getName(), habit.getCreatedAt());
    }
}
