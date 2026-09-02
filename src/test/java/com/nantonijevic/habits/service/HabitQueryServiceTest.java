package com.nantonijevic.habits.service;

import com.nantonijevic.habits.domain.Habit;
import com.nantonijevic.habits.domain.HabitNotFoundException;
import com.nantonijevic.habits.dto.HabitCompletionRateResponse;
import com.nantonijevic.habits.repository.HabitCompletionRepository;
import com.nantonijevic.habits.repository.HabitCompletionStatRepository;
import com.nantonijevic.habits.repository.HabitMapper;
import com.nantonijevic.habits.repository.HabitSearchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HabitQueryServiceTest {

    private static final Long OWNER_ID = 101L;

    private static final ZoneId TEST_ZONE =
        ZoneId.of("UTC");

    private static final Instant FIXED =
        Instant.parse("2026-01-15T12:00:00Z");

    @Mock
    private HabitSearchRepository habitSearchRepository;

    @Mock
    private HabitMapper habitMapper;

    @Mock
    private HabitCompletionRepository completionRepository;

    @Mock
    private HabitCompletionStatRepository completionStatRepository;

    @Mock
    private Clock clock;

    @InjectMocks
    private HabitQueryService habitQueryService;

    @BeforeEach
    void useUtcZoneByDefault() {
        lenient()
            .when(clock.getZone())
            .thenReturn(TEST_ZONE);
    }

    @Test
    void completionRateRoundsOneThirdToFourDecimalPlaces() {
        Long habitId = 42L;
        Habit habit = new Habit("Read", FIXED);

        LocalDate createdDate =
            LocalDate.ofInstant(
                habit.getCreatedAt(),
                TEST_ZONE
            );

        LocalDate from =
            createdDate.plusDays(1);

        LocalDate to =
            from.plusDays(2);

        habit.setScheduledDays(
            EnumSet.of(
                from.getDayOfWeek(),
                from.plusDays(1).getDayOfWeek(),
                to.getDayOfWeek()
            )
        );

        when(habitMapper.findById(
                OWNER_ID,
                habitId))
            .thenReturn(habit);

        when(
            completionStatRepository
                .findCompletedDatesInPeriod(
                    habitId,
                    from,
                    to
                )
        ).thenReturn(
            List.of(from)
        );

        var response =
            habitQueryService.getCompletionRate(
               OWNER_ID,
                habitId,
                from,
                to
            );

        assertThat(response.scheduled())
            .isEqualTo(3);

        assertThat(response.completed())
            .isEqualTo(1);

        assertThat(response.rate())
            .isEqualByComparingTo("0.3333");
    }

    @Test
    void completionRateReturnsEmptyResponseWithoutQueryWhenHabitWasCreatedAfterWindow() {
        Long habitId = 42L;
        Habit habit = new Habit("Read", FIXED);

        LocalDate from =
            LocalDate.of(2000, 1, 1);

        LocalDate to =
            LocalDate.of(2000, 1, 31);

        when(habitMapper.findById(
                OWNER_ID,
                habitId))
            .thenReturn(habit);

        var response =
            habitQueryService.getCompletionRate(
               OWNER_ID,
                habitId,
                from,
                to
            );

        assertThat(response.scheduled())
            .isZero();

        assertThat(response.completed())
            .isZero();

        assertThat(response.rate())
            .isNull();

        verifyNoInteractions(
            completionStatRepository
        );
    }

    @Test
    void completionRateUsesBusinessClockZoneWhenClampingCreationDate() {
        Long habitId = 42L;

        ZoneId businessZone =
            ZoneId.of("Pacific/Kiritimati");

        Instant createdAt =
            Instant.parse(
                "2026-08-01T21:30:00Z"
            );

        LocalDate from =
            LocalDate.of(2026, 8, 1);

        LocalDate to =
            LocalDate.of(2026, 8, 1);

        Habit habit =
            new Habit("Read", createdAt);

        when(clock.getZone())
            .thenReturn(businessZone);

        when(habitMapper.findById(
                OWNER_ID,
                habitId))
            .thenReturn(habit);

        HabitCompletionRateResponse response =
            habitQueryService.getCompletionRate(
               OWNER_ID,
                habitId,
                from,
                to
            );

        assertThat(response.scheduled())
            .isZero();

        assertThat(response.completed())
            .isZero();

        assertThat(response.rate())
            .isNull();

        verify(
            clock,
            atLeastOnce()
        ).getZone();

        verifyNoInteractions(
            completionStatRepository
        );
    }

    @Test
    void completionRateStartsAtHabitCreationDateWhenHabitIsYoungerThanWindow() {
        Long habitId = 42L;

        Habit habit =
            new Habit("Read", FIXED);

        Instant createdAt =
            Instant.parse(
                "2024-01-03T12:00:00Z"
            );

        ReflectionTestUtils.setField(
            habit,
            "createdAt",
            createdAt
        );

        LocalDate createdDate =
            LocalDate.ofInstant(
                createdAt,
                TEST_ZONE
            );

        LocalDate from =
            createdDate.minusDays(2);

        LocalDate to =
            createdDate.plusDays(2);

        habit.setScheduledDays(
            EnumSet.allOf(DayOfWeek.class)
        );

        when(habitMapper.findById(
                OWNER_ID,
                habitId))
            .thenReturn(habit);

        when(
            completionStatRepository
                .findCompletedDatesInPeriod(
                    habitId,
                    createdDate,
                    to
                )
        ).thenReturn(
            List.of(
                createdDate,
                createdDate.plusDays(1)
            )
        );

        var response =
            habitQueryService.getCompletionRate(
               OWNER_ID,
                habitId,
                from,
                to
            );

        assertThat(response.scheduled())
            .isEqualTo(3);

        assertThat(response.completed())
            .isEqualTo(2);

        assertThat(response.rate())
            .isEqualByComparingTo("0.6667");

        verify(completionStatRepository)
            .findCompletedDatesInPeriod(
                habitId,
                createdDate,
                to
            );
    }

    @Test
    void completionRateThrowsNotFoundWhenHabitDoesNotExist() {
        Long habitId = 42L;

        LocalDate from =
            LocalDate.of(2024, 1, 1);

        LocalDate to =
            LocalDate.of(2024, 1, 31);

        when(habitMapper.findById(
                OWNER_ID,
                habitId))
            .thenReturn(null);

        assertThatThrownBy(
            () ->
                habitQueryService
                    .getCompletionRate(
                        OWNER_ID, habitId,
                        from,
                        to
                    )
        )
            .isInstanceOf(
                HabitNotFoundException.class
            )
            .hasMessage(
                "Habit not found: " + habitId
            );

        verifyNoInteractions(
            completionStatRepository
        );
    }

    @Test
    void completionRateExcludesCompletionsOnUnscheduledDays() {
        Long habitId = 42L;

        Habit habit =
            new Habit("Read", FIXED);

        Instant createdAt =
            Instant.parse(
                "2024-01-01T12:00:00Z"
            );

        ReflectionTestUtils.setField(
            habit,
            "createdAt",
            createdAt
        );

        LocalDate scheduledDate =
            LocalDate.ofInstant(
                createdAt,
                TEST_ZONE
            );

        LocalDate offDay =
            scheduledDate.plusDays(1);

        habit.setScheduledDays(
            EnumSet.of(
                scheduledDate.getDayOfWeek()
            )
        );

        when(habitMapper.findById(
                OWNER_ID,
                habitId))
            .thenReturn(habit);

        when(
            completionStatRepository
                .findCompletedDatesInPeriod(
                    habitId,
                    scheduledDate,
                    offDay
                )
        ).thenReturn(
            List.of(
                scheduledDate,
                offDay
            )
        );

        var response =
            habitQueryService.getCompletionRate(
               OWNER_ID,
                habitId,
                scheduledDate,
                offDay
            );

        assertThat(response.scheduled())
            .isEqualTo(1);

        assertThat(response.completed())
            .isEqualTo(1);

        assertThat(response.rate())
            .isEqualByComparingTo("1.0000");
    }

    @Test
    void completionRateReturnsNullWhenWindowHasNoScheduledOccurrences() {
        Long habitId = 42L;

        Habit habit =
            new Habit("Read", FIXED);

        LocalDate createdDate =
            LocalDate.ofInstant(
                habit.getCreatedAt(),
                TEST_ZONE
            );

        LocalDate from =
            createdDate.plusDays(1);

        LocalDate to =
            from.plusDays(1);

        DayOfWeek scheduledDayOutsideWindow =
            to.plusDays(1).getDayOfWeek();

        habit.setScheduledDays(
            EnumSet.of(
                scheduledDayOutsideWindow
            )
        );

        when(habitMapper.findById(
                OWNER_ID,
                habitId))
            .thenReturn(habit);

        when(
            completionStatRepository
                .findCompletedDatesInPeriod(
                    habitId,
                    from,
                    to
                )
        ).thenReturn(List.of());

        var response =
            habitQueryService.getCompletionRate(
               OWNER_ID,
                habitId,
                from,
                to
            );

        assertThat(response.scheduled())
            .isZero();

        assertThat(response.completed())
            .isZero();

        assertThat(response.rate())
            .isNull();
    }

    @Test
    void completionRateReturnsZeroWhenSingleScheduledDayWasNotCompleted() {
        Long habitId = 42L;

        Habit habit =
            new Habit("Read", FIXED);

        LocalDate createdDate =
            LocalDate.ofInstant(
                habit.getCreatedAt(),
                TEST_ZONE
            );

        LocalDate date =
            createdDate.plusDays(1);

        habit.setScheduledDays(
            EnumSet.of(
                date.getDayOfWeek()
            )
        );

        when(habitMapper.findById(
                OWNER_ID,
                habitId))
            .thenReturn(habit);

        when(
            completionStatRepository
                .findCompletedDatesInPeriod(
                    habitId,
                    date,
                    date
                )
        ).thenReturn(List.of());

        var response =
            habitQueryService.getCompletionRate(
               OWNER_ID,
                habitId,
                date,
                date
            );

        assertThat(response.scheduled())
            .isEqualTo(1);

        assertThat(response.completed())
            .isZero();

        assertThat(response.rate())
            .isEqualByComparingTo("0.0000");
    }

    @Test
    void dashboardDoesNotQueryStatsWhenThereAreNoActiveHabits() {
        LocalDate today =
            LocalDate.of(2026, 8, 1);

        when(habitMapper.findActive(OWNER_ID))
            .thenReturn(List.of());

        var dashboard =
            habitQueryService.getDashboardStats(
               OWNER_ID,
                today
            );

        assertThat(dashboard.dueToday())
            .isZero();

        assertThat(dashboard.completedToday())
            .isZero();

        assertThat(dashboard.activeStreaks())
            .isZero();

        assertThat(
            dashboard.longestActiveStreak()
        ).isZero();

        assertThat(dashboard.totalHabits())
            .isZero();

        verify(habitMapper).findActive(OWNER_ID);

        verifyNoInteractions(
            completionStatRepository
        );
    }
}
