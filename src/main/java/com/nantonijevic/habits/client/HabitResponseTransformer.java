package com.nantonijevic.habits.client;

import com.nantonijevic.habits.dto.HabitResponse;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

@Component
public class HabitResponseTransformer {

    public HabitResponse transform(
        HabitResponse source,
        ClientTier tier
    ) {
        return new HabitResponse(
            source.id(),
            source.name(),
            tier.exposesScheduledDays()
                ? source.scheduledDays()
                : null,
            source.completionCount(),
            source.currentStreak(),
            tier.exposesArchived()
                ? source.archived()
                : null,
            tier.exposesCreatedAt()
                ? source.createdAt()
                : null
        );
    }

    public Page<HabitResponse> transform(
        Page<HabitResponse> source,
        ClientTier tier
    ) {
        return source.map(response ->
            transform(response, tier)
        );
    }
}
