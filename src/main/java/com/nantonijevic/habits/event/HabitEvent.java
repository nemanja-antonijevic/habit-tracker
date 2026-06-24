package com.nantonijevic.habits.event;

import java.time.LocalDate;

public sealed interface HabitEvent permits HabitCompletedEvent, HabitUncompletedEvent {
    Long habitId();
    LocalDate completedOn();
}

