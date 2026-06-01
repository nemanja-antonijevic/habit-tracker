package com.nantonijevic.habits.habit;

import java.time.Instant;

public record StatsResponse(Long id, int completionCount, int longestStreak, int currentStreak, Instant lastCompletedAt) {
    public static StatsResponse from(Habit habit) {
        return new StatsResponse(habit.getId(), habit.getCompletionCount(), habit.getLongestStreak(), habit.getCurrentStreak(), habit.getLastCompletedAt());
    }
}
