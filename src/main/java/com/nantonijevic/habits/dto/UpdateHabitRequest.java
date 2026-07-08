package com.nantonijevic.habits.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.DayOfWeek;
import java.util.Set;

public record UpdateHabitRequest(
        @NotNull Long version,
        @NotBlank @Size(max = 255) String name,

        @Size(min = 1)
        Set<DayOfWeek> scheduledDays
) {}
