package com.nantonijevic.habits.dto;

import java.math.BigDecimal;

public record HabitCompletionRateResponse(
    long scheduled,
    long completed,
    BigDecimal rate
) {
}
