package com.nantonijevic.habits.service;

import com.nantonijevic.habits.domain.Habit;
import com.nantonijevic.habits.repository.HabitCompletionRepository;
import com.nantonijevic.habits.repository.HabitCompletionStatRepository;
import com.nantonijevic.habits.repository.HabitMapper;
import com.nantonijevic.habits.repository.HabitSearchRepository;
import com.nantonijevic.habits.repository.HabitWriteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HabitCommandQueryClockConsistencyTest {

    private static final Long OWNER_ID = 101L;

    private static final ZoneId TEST_ZONE =
        ZoneId.of("UTC");

    @Mock
    private HabitSearchRepository habitSearchRepository;

    @Mock
    private HabitWriteRepository habitWriteRepository;

    @Mock
    private HabitMapper habitMapper;

    @Mock
    private HabitCompletionRepository completionRepository;

    @Mock
    private HabitCompletionStatRepository completionStatRepository;

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @Mock
    private TransactionTemplate transactionTemplate;

    @Mock
    private Clock clock;

    @InjectMocks
    private HabitCommandService habitCommandService;

    @InjectMocks
    private HabitQueryService habitQueryService;

    @BeforeEach
    void executeTransactionCallbacks() {
        lenient()
            .when(transactionTemplate.execute(any()))
            .thenAnswer(invocation -> {
                TransactionCallback<?> callback =
                    invocation.getArgument(0);

                return callback.doInTransaction(
                    mock(TransactionStatus.class)
                );
            });
    }

    @BeforeEach
    void useUtcZone() {
        lenient()
            .when(clock.getZone())
            .thenReturn(TEST_ZONE);
    }

    @Test
    void completionRateIncludesBusinessDateOnWhichNewHabitWasCompleted() {
        Long habitId = 42L;

        // The instant is built in TEST_ZONE, not the host zone, so that a host
        // running ahead of UTC reads createdAt as the following day. That is what
        // makes the old implementation clamp the whole completion-rate window
        // away; with both sides in the same zone the offsets cancel and the
        // clamp never triggers, which is why this fixture used to pass with the
        // bug in place.
        LocalDate businessDate =
            LocalDate.of(2020, 1, 1);

        Instant businessInstant =
            businessDate
                .atTime(12, 0)
                .atZone(TEST_ZONE)
                .toInstant();

        when(clock.instant())
            .thenReturn(businessInstant);

        when(
            habitWriteRepository.save(
                any(Habit.class)
            )
        ).thenAnswer(
            invocation ->
                invocation.getArgument(0)
        );

        Habit habit =
            habitCommandService.create(
                OWNER_ID,
                "Read",
                EnumSet.allOf(
                    DayOfWeek.class
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
                    businessDate,
                    businessDate
                )
        ).thenReturn(
            List.of(businessDate)
        );

        habitCommandService.complete(
                OWNER_ID,
                habitId,
            businessDate
        );

        var response =
            habitQueryService.getCompletionRate(
                OWNER_ID,
                habitId,
                businessDate,
                businessDate
            );

        assertThat(response.scheduled())
            .isEqualTo(1);

        assertThat(response.completed())
            .isEqualTo(1);

        assertThat(response.rate())
            .isEqualByComparingTo("1.0000");
    }
}
