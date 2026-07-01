package com.nantonijevic.habits.exception;

public class InvalidDateRangeException extends RuntimeException {

    public InvalidDateRangeException() {
        super("'from' must not be after 'to'");
    }
}
