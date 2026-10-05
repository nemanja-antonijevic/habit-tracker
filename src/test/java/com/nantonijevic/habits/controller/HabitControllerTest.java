package com.nantonijevic.habits.controller;

import com.nantonijevic.habits.client.ClientContext;
import com.nantonijevic.habits.client.ClientTier;
import com.nantonijevic.habits.client.HabitResponseTransformer;
import com.nantonijevic.habits.domain.Habit;
import com.nantonijevic.habits.domain.HabitSkip;
import com.nantonijevic.habits.dto.WeekdayBreakdownResponse;
import com.nantonijevic.habits.service.HabitCommandService;
import com.nantonijevic.habits.service.HabitQueryService;
import com.nantonijevic.habits.service.HabitSkipService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
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

    @Mock
    private HabitSkipService habitSkipService;

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
                habitResponseTransformer,
                habitSkipService
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

    @Test
    void getCurrentMonthSkipUsesInjectedClockForBusinessDate() {
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

        when(
            habitSkipService.getCurrentMonthSkip(
                OWNER_ID,
                42L,
                expectedBusinessDate
            )
        ).thenReturn(
            new HabitSkip(
                42L,
                expectedBusinessDate
            )
        );

        HabitController controller =
            new HabitController(
                habitCommandService,
                habitQueryService,
                clock,
                habitResponseTransformer,
                habitSkipService
            );

        controller.getCurrentMonthSkip(
            42L,
            CLIENT_CONTEXT
        );

        verify(habitSkipService)
            .getCurrentMonthSkip(
                OWNER_ID,
                42L,
                expectedBusinessDate
            );
    }

    @Test
    void removeCurrentMonthSkipUsesInjectedClockForBusinessDate() {
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

        HabitController controller =
            new HabitController(
                habitCommandService,
                habitQueryService,
                clock,
                habitResponseTransformer,
                habitSkipService
            );

        controller.removeCurrentMonthSkip(
            42L,
            CLIENT_CONTEXT
        );

        verify(habitSkipService)
            .removeCurrentMonthSkip(
                OWNER_ID,
                42L,
                expectedBusinessDate
            );
    }

    @Test
    void skipUsesInjectedClockForBusinessDate() {
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

        when(
            habitSkipService.skip(
                OWNER_ID,
                42L,
                expectedBusinessDate
            )
        )
            .thenReturn(
                new HabitSkip(
                    42L,
                    expectedBusinessDate
                )
            );

        HabitController controller =
            new HabitController(
                habitCommandService,
                habitQueryService,
                clock,
                habitResponseTransformer,
                habitSkipService
            );

        controller.skip(
            42L,
            CLIENT_CONTEXT
        );

        verify(habitSkipService).skip(
            OWNER_ID,
            42L,
            expectedBusinessDate
        );
    }

    @Test
    void weekdayBreakdownPassesOwnerAndRangeToQueryService() {
        LocalDate from =
            LocalDate.of(2026, 7, 1);
        LocalDate to =
            LocalDate.of(2026, 7, 31);

        Map<DayOfWeek, WeekdayBreakdownResponse>
            expected =
            Map.of(
                DayOfWeek.MONDAY,
                new WeekdayBreakdownResponse(
                    4,
                    3
                )
            );

        when(
            habitQueryService
                .getWeekdayBreakdown(
                    OWNER_ID,
                    42L,
                    from,
                    to
                )
        ).thenReturn(expected);

        HabitController controller =
            new HabitController(
                habitCommandService,
                habitQueryService,
                Clock.systemUTC(),
                habitResponseTransformer,
                habitSkipService
            );

        var response =
            controller.getWeekdayBreakdown(
                42L,
                from,
                to,
                CLIENT_CONTEXT
            );

        assertThat(response)
            .isSameAs(expected);

        verify(habitQueryService)
            .getWeekdayBreakdown(
                OWNER_ID,
                42L,
                from,
                to
            );
    }
}
