package com.nantonijevic.habits.exception;

import com.nantonijevic.habits.domain.HabitNotFoundException;
import com.nantonijevic.habits.domain.HabitVersionConflictException;
import com.nantonijevic.habits.domain.InvalidHabitStateException;
import com.nantonijevic.habits.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(HabitNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handle(HabitNotFoundException e) {
        return new ErrorResponse(e.getMessage());
    }

    @ExceptionHandler(InvalidHabitStateException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handle(InvalidHabitStateException e) {
        return new ErrorResponse(e.getMessage());
    }

    @ExceptionHandler(HabitVersionConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handle(HabitVersionConflictException e) {
        return new ErrorResponse(e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handle(MethodArgumentNotValidException e) {
        String message = e.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(fe -> fe.getField() + " " + fe.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return new ErrorResponse(message);
    }
}
