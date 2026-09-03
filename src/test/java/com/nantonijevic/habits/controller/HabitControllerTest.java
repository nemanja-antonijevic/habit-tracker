package com.nantonijevic.habits.controller;

import com.nantonijevic.habits.client.ClientContext;
import com.nantonijevic.habits.client.ClientTier;
import com.nantonijevic.habits.client.HabitResponseTransformer;
import com.nantonijevic.habits.domain.Habit;
import com.nantonijevic.habits.service.HabitCommandService;
import com.nantonijevic.habits.service.HabitQueryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HabitControllerTest {

    private static final Long OWNER_ID = 101L;

    private static final ClientContext CLIENT_CONTEXT =
        new ClientContext(OWNER_ID, ClientTier.INTERNAL);

    @Mock
    private HabitCommandService habitCommandService;

    @Mock
    private HabitQueryService habitQueryService;

    @Mock
    private HabitResponseTransformer habitResponseTransformer;

    @Test
    void completeUsesInjectedClockForBusinessDate() {
        Clock clock =
            Clock.fixed(
                Instant.parse(
                    "2040-01-15T12:00:00Z"
                ),
                ZoneId.of(
                    "Europe/Belgrade"
                )
            );

        LocalDate expectedBusinessDate =
            LocalDate.now(clock);

        Habit habit =
            new Habit(OWNER_ID, "Read", clock.instant());

        when(
            habitCommandService.complete(
                eq(OWNER_ID),
                eq(42L),
                any(LocalDate.class)
            )
        )
            .thenReturn(habit);

        HabitController controller =
            new HabitController(
                habitCommandService,
                habitQueryService,
                clock,
                habitResponseTransformer
            );

        controller.complete(
            42L,
            CLIENT_CONTEXT
        );

        verify(habitCommandService)
            .complete(
                OWNER_ID,
                42L,
                expectedBusinessDate
            );
    }
}
