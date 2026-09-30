package com.nantonijevic.habits.domain;

import java.time.LocalDate;

public class HabitSkipInUseException
    extends RuntimeException {

    public HabitSkipInUseException(
        Long habitId,
        LocalDate skippedOn
    ) {
        super(
            "Skip on "
                + skippedOn
                + " cannot be removed because the streak depends on it for habit "
                + habitId
        );
    }
}
