package com.nantonijevic.habits.controller;

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

    @Mock
    private HabitCommandService habitCommandService;

    @Mock
    private HabitQueryService habitQueryService;

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
            new Habit("Read", clock.instant());

        when(
            habitCommandService.complete(
                eq(42L),
                any(LocalDate.class)
            )
        )
            .thenReturn(habit);

        HabitController controller =
            new HabitController(
                habitCommandService,
                habitQueryService,
                clock
            );

        controller.complete(42L);

        verify(habitCommandService)
            .complete(
                42L,
                expectedBusinessDate
            );
    }
}
