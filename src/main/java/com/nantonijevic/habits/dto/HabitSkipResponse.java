package com.nantonijevic.habits.dto;

import com.nantonijevic.habits.domain.HabitSkip;

import java.time.LocalDate;

public record HabitSkipResponse(
    LocalDate skippedOn
) {

    public static HabitSkipResponse from(
        HabitSkip skip
    ) {
        return new HabitSkipResponse(
            skip.skippedOn()
        );
    }
}
