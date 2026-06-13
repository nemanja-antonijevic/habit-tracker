package com.nantonijevic.habits.exception;

import com.nantonijevic.habits.domain.HabitNotFoundException;
import com.nantonijevic.habits.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(HabitNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handle(HabitNotFoundException e) {
        return new ErrorResponse(e.getMessage());
    }
}
