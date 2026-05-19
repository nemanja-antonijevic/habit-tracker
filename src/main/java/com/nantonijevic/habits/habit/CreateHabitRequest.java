package com.nantonijevic.habits.habit;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateHabitRequest(
        @NotBlank @Size(max = 255) String name
){

}