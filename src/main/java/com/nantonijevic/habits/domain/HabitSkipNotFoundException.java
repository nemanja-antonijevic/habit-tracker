package com.nantonijevic.habits.domain;

import java.time.LocalDate;
import java.time.YearMonth;

public class HabitSkipNotFoundException
    extends RuntimeException {

    public HabitSkipNotFoundException(
        Long habitId,
        LocalDate date
    ) {
        super(
            "Monthly skip not found for habit "
                + habitId
                + " in "
                + YearMonth.from(date)
        );
    }
}
