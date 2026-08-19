package com.nantonijevic.habits.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.nantonijevic.habits.domain.Habit;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Set;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record HabitResponse(
        Long id,
        String name,
        Set<DayOfWeek> scheduledDays,
        int completionCount,
        int currentStreak,
        Boolean archived,
        Instant createdAt
) {
    public static HabitResponse from(
        Habit habit,
        LocalDate today,
        ZoneId zone
    ) {
        return new HabitResponse(
            habit.getId(),
            habit.getName(),
            habit.getScheduledDays(),
            habit.getCompletionCount(),
            habit.effectiveCurrentStreak(
                today,
                zone
            ),
            habit.isArchived(),
            habit.getCreatedAt()
        );
    }
}
