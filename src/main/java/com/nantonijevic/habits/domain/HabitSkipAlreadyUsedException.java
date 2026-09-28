package com.nantonijevic.habits.domain;

import java.time.LocalDate;
import java.time.YearMonth;

public class HabitSkipAlreadyUsedException
    extends RuntimeException {

    public HabitSkipAlreadyUsedException(
        Long habitId,
        LocalDate skippedOn
    ) {
        super(
            "Monthly skip already used for habit "
                + habitId
                + " in "
                + YearMonth.from(skippedOn)
        );
    }
}
