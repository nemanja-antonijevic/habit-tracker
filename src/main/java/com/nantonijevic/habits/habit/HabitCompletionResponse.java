package com.nantonijevic.habits.habit;

import java.time.LocalDate;

public record HabitCompletionResponse(Long id, LocalDate completedOn) {
    public static HabitCompletionResponse from(HabitCompletion completion) {
        return new HabitCompletionResponse(completion.getId(), completion.getCompletedOn());
    }
}