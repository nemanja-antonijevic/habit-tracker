package com.nantonijevic.habits.event;

import java.time.LocalDate;

public record HabitUncompletedEvent(
        Long habitId,
        LocalDate completedOn
) implements HabitEvent{
}
