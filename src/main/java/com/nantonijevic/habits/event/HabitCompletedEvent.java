package com.nantonijevic.habits.event;

import java.time.LocalDate;

public record HabitCompletedEvent(
        Long habitId,
        LocalDate completedOn,
        int currentStreak,
        int completionCount
){
}
