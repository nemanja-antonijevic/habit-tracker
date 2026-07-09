package com.nantonijevic.habits.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record BulkCompleteRequest(
        @NotEmpty
        @Size(max = 100)
        List<Long> habitIds
) {
}
