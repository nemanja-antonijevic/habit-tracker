package com.nantonijevic.habits.dto;

import java.time.LocalDate;

public record HabitStatsView(long completionCount, Integer longestStreak, LocalDate lastCompletedOn, Integer currentStreak) {
}
