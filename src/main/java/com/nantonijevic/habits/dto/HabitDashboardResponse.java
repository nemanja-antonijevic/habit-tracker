package com.nantonijevic.habits.dto;

public record HabitDashboardResponse(
    long dueToday,
    long completedToday,
    long activeStreaks,
    int longestActiveStreak,
    long totalHabits
) {
}
