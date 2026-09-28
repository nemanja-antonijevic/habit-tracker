package com.nantonijevic.habits.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;


import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HabitSkipTest {

    @Test
    void derivesCalendarMonthStartFromSkippedDate() {
        HabitSkip skip = new HabitSkip(
            42L,
            LocalDate.of(2026, 7, 18)
        );

        assertThat(skip.monthStart())
            .isEqualTo(
                LocalDate.of(2026, 7, 1)
            );
    }

    @Test
    void rejectsMissingHabitId() {
        assertThatThrownBy(
            () -> new HabitSkip(
                null,
                LocalDate.of(2026, 7, 18)
            )
        )
            .isInstanceOf(
                NullPointerException.class
            )
            .hasMessage(
                "habitId must not be null"
            );
    }

    @Test
    void rejectsMissingSkippedDate() {
        assertThatThrownBy(
            () -> new HabitSkip(
                42L,
                null
            )
        )
            .isInstanceOf(
                NullPointerException.class
            )
            .hasMessage(
                "skippedOn must not be null"
            );
    }
}
