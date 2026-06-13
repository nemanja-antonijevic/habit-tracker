package com.nantonijevic.habits.domain;

public class HabitVersionConflictException extends RuntimeException {

    public HabitVersionConflictException(Long habitId) {
        super("Habit version conflict: " + habitId);
    }
}
