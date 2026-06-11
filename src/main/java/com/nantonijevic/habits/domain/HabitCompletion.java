package com.nantonijevic.habits.domain;

import jakarta.persistence.*;

import java.time.LocalDate;

@Entity
@Table(name = "habit_completions")
public class HabitCompletion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "habit_id", nullable = false)
    private Long habitId;

    @Column(name = "completed_on",  nullable = false)
    private LocalDate completedOn;

    protected HabitCompletion() {
    }

    public HabitCompletion(Long habitId, LocalDate completedOn) {
        this.habitId = habitId;
        this.completedOn = completedOn;
    }

    public Long getId() {
        return id;
    }

    public Long getHabitId() {
        return habitId;
    }

    public LocalDate getCompletedOn() {
        return completedOn;
    }
}
