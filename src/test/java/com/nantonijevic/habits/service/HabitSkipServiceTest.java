package com.nantonijevic.habits.service;

import com.nantonijevic.habits.domain.Habit;
import com.nantonijevic.habits.domain.HabitNotFoundException;
import com.nantonijevic.habits.domain.HabitSkip;
import com.nantonijevic.habits.domain.HabitSkipAlreadyUsedException;
import com.nantonijevic.habits.event.DashboardChangedEvent;
import com.nantonijevic.habits.repository.HabitMapper;
import com.nantonijevic.habits.repository.HabitSkipRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HabitSkipServiceTest {

    private static final Long OWNER_ID = 101L;

    private static final Long HABIT_ID = 202L;

    private static final LocalDate SKIPPED_ON =
        LocalDate.of(2026, 7, 6);

    private static final Clock CLOCK =
        Clock.fixed(
            Instant.parse(
                "2026-07-06T12:00:00Z"
            ),
            ZoneId.of("UTC")
        );

    @Mock
    private HabitMapper habitMapper;

    @Mock
    private HabitSkipRepository skipRepository;

    @Mock
    private ApplicationEventPublisher
        eventPublisher;

    private HabitSkipService service;

    @BeforeEach
    void setUp() {
        service = new HabitSkipService(
            habitMapper,
            skipRepository,
            eventPublisher,
            CLOCK
        );
    }

    @Test
    void savesSkipForOwnedHabit() {
        Habit habit = habit();

        when(
            habitMapper.findById(
                OWNER_ID,
                HABIT_ID
            )
        )
            .thenReturn(habit);
        when(
            skipRepository
                .findByHabitIdAndMonthStart(
                    HABIT_ID,
                    LocalDate.of(2026, 7, 1)
                )
        )
            .thenReturn(Optional.empty());
        when(
            skipRepository.saveAndFlush(
                any(HabitSkip.class)
            )
        )
            .thenAnswer(
                invocation ->
                    invocation.getArgument(0)
            );

        HabitSkip saved = service.skip(
            OWNER_ID,
            HABIT_ID,
            SKIPPED_ON
        );

        assertThat(saved.habitId())
            .isEqualTo(HABIT_ID);
        assertThat(saved.skippedOn())
            .isEqualTo(SKIPPED_ON);
        assertThat(saved.monthStart())
            .isEqualTo(
                LocalDate.of(2026, 7, 1)
            );

        verify(eventPublisher).publishEvent(
            isA(DashboardChangedEvent.class)
        );
    }

    @Test
    void rejectsSecondSkipInSameMonth() {
        Habit habit = habit();

        when(
            habitMapper.findById(
                OWNER_ID,
                HABIT_ID
            )
        )
            .thenReturn(habit);
        when(
            skipRepository
                .findByHabitIdAndMonthStart(
                    HABIT_ID,
                    LocalDate.of(2026, 7, 1)
                )
        )
            .thenReturn(
                Optional.of(
                    new HabitSkip(
                        HABIT_ID,
                        LocalDate.of(2026, 7, 2)
                    )
                )
            );

        assertThatThrownBy(
            () -> service.skip(
                OWNER_ID,
                HABIT_ID,
                SKIPPED_ON
            )
        )
            .isInstanceOf(
                HabitSkipAlreadyUsedException.class
            )
            .hasMessageContaining("2026-07");

        verify(
            skipRepository,
            never()
        )
            .saveAndFlush(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void rejectsHabitOutsideOwnerScope() {
        when(
            habitMapper.findById(
                OWNER_ID,
                HABIT_ID
            )
        )
            .thenReturn(null);

        assertThatThrownBy(
            () -> service.skip(
                OWNER_ID,
                HABIT_ID,
                SKIPPED_ON
            )
        )
            .isInstanceOf(
                HabitNotFoundException.class
            );

        verifyNoInteractions(
            skipRepository,
            eventPublisher
        );
    }

    @Test
    void mapsConcurrentMonthlyConstraintToConflict() {
        Habit habit = habit();

        when(
            habitMapper.findById(
                OWNER_ID,
                HABIT_ID
            )
        )
            .thenReturn(habit);
        when(
            skipRepository
                .findByHabitIdAndMonthStart(
                    HABIT_ID,
                    LocalDate.of(2026, 7, 1)
                )
        )
            .thenReturn(Optional.empty());
        when(
            skipRepository.saveAndFlush(
                any(HabitSkip.class)
            )
        )
            .thenThrow(
                new DataIntegrityViolationException(
                    "uq_habit_skips_habit_month"
                )
            );

        assertThatThrownBy(
            () -> service.skip(
                OWNER_ID,
                HABIT_ID,
                SKIPPED_ON
            )
        )
            .isInstanceOf(
                HabitSkipAlreadyUsedException.class
            )
            .hasMessageContaining("2026-07");

        verifyNoInteractions(eventPublisher);
    }

    private Habit habit() {
        return new Habit(
            OWNER_ID,
            "Workout",
            Instant.parse(
                "2026-01-15T12:00:00Z"
            )
        );
    }
}
