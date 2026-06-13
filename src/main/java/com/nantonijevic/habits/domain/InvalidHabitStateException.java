package com.nantonijevic.habits.domain;

public class InvalidHabitStateException extends RuntimeException {
    public InvalidHabitStateException(String message) {
        super(message);
    }
}
