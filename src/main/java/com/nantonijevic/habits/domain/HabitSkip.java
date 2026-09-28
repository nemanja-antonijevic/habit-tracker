package com.nantonijevic.habits.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.util.Objects;

@Entity
@Table(name = "habit_skips")
public class HabitSkip {

    @Id
    @GeneratedValue(
        strategy = GenerationType.IDENTITY
    )
    private Long id;

    @Column(
        name = "habit_id",
        nullable = false
    )
    private Long habitId;

    @Column(
        name = "skipped_on",
        nullable = false
    )
    private LocalDate skippedOn;

    @Column(
        name = "month_start",
        nullable = false
    )
    private LocalDate monthStart;

    protected HabitSkip() {
    }

    public HabitSkip(
        Long habitId,
        LocalDate skippedOn
    ) {
        this.habitId = Objects.requireNonNull(
            habitId,
            "habitId must not be null"
        );
        this.skippedOn = Objects.requireNonNull(
            skippedOn,
            "skippedOn must not be null"
        );
        this.monthStart =
            skippedOn.withDayOfMonth(1);
    }

    public Long getId() {
        return id;
    }

    public Long habitId() {
        return habitId;
    }

    public LocalDate skippedOn() {
        return skippedOn;
    }

    public LocalDate monthStart() {
        return monthStart;
    }
}
