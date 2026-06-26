package com.nantonijevic.habits.dto;

import com.nantonijevic.habits.domain.HabitCompletion;

import java.time.LocalDate;

public record HabitCompletionResponse(LocalDate completedOn) {
    public static HabitCompletionResponse from(HabitCompletion completion) {
        return new HabitCompletionResponse(completion.getCompletedOn());
    }
}
