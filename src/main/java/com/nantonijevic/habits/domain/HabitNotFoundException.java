package com.nantonijevic.habits.domain;

public class HabitNotFoundException extends RuntimeException {

    public HabitNotFoundException(Long habitId) {
        super("Habit not found: " + habitId);
    }
}
