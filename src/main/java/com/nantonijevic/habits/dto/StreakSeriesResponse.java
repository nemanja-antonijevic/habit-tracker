package com.nantonijevic.habits.dto;

import com.nantonijevic.habits.domain.Habit;

import java.time.LocalDate;

public record StreakSeriesResponse(
    LocalDate start,
    LocalDate end,
    int length,
    boolean current
) {

    public static StreakSeriesResponse from(
        Habit.StreakSeries series
    ) {
        return new StreakSeriesResponse(
            series.start(),
            series.end(),
            series.length(),
            series.current()
        );
    }
}
