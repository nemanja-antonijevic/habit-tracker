package com.nantonijevic.habits.dto;

import com.nantonijevic.habits.domain.Habit;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;

public record HabitResponse(
        Long id,
        String name,
        Set<DayOfWeek> scheduledDays,
        int completionCount,
        int currentStreak,
        boolean archived,
        Instant createdAt
) {
    public static HabitResponse from(Habit habit, LocalDate today) {
        return new HabitResponse(
                habit.getId(),
                habit.getName(),
                habit.getScheduledDays(),
                habit.getCompletionCount(),
                habit.effectiveCurrentStreak(today),
                habit.isArchived(),
                habit.getCreatedAt());
    }
}
