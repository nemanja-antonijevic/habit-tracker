package com.nantonijevic.habits.dto;

import java.util.List;

public record BulkCompleteResponse(
        List<Long> completed,
        List<Long> skipped,
        List<Long> failed,
        List<Long> notFound
) {
}
