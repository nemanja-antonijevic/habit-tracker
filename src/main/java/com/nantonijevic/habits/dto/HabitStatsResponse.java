package com.nantonijevic.habits.dto;

import java.time.LocalDate;

public record HabitStatsResponse(long completionCount, int longestStreak, LocalDate lastCompletedOn) {
    public static HabitStatsResponse from(HabitStatsView view){
        return new HabitStatsResponse(view.completionCount(), view.longestStreak() == null ? 0 : view.longestStreak(), view.lastCompletedOn());
    }
}
