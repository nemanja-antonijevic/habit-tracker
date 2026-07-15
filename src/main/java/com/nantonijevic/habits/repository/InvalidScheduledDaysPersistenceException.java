package com.nantonijevic.habits.repository;

public class InvalidScheduledDaysPersistenceException extends RuntimeException {

    public InvalidScheduledDaysPersistenceException(String message) {
        super(message);
    }
}
