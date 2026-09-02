package com.nantonijevic.habits.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.nantonijevic.habits.domain.Habit;
import com.nantonijevic.habits.domain.HabitCompletion;
import com.nantonijevic.habits.domain.HabitNotFoundException;
import com.nantonijevic.habits.domain.HabitVersionConflictException;
import com.nantonijevic.habits.event.DashboardChangedEvent;
import com.nantonijevic.habits.repository.HabitCompletionRepository;
import com.nantonijevic.habits.repository.HabitMapper;
import com.nantonijevic.habits.repository.HabitWriteRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HabitCommandServiceTest {

    private static final Long OWNER_ID = 101L;

    private static final ZoneId TEST_ZONE =
        ZoneId.of("UTC");

    private static final Instant FIXED =
        Instant.parse("2026-01-15T12:00:00Z");

    @Mock
    private HabitWriteRepository habitWriteRepository;

    @Mock
    private HabitMapper habitMapper;

    @Mock
    private HabitCompletionRepository completionRepository;

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @Mock
    private TransactionTemplate transactionTemplate;

    @Mock
    private Clock clock;

    @InjectMocks
    private HabitCommandService habitCommandService;

    private Logger habitServiceLogger;

    private ListAppender<ILoggingEvent> logAppender;

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
    void useUtcZoneByDefault() {
        lenient()
            .when(clock.getZone())
            .thenReturn(TEST_ZONE);
    }

    @BeforeEach
    void attachHabitServiceLogAppender() {
        habitServiceLogger =
            (Logger) LoggerFactory.getLogger(HabitCommandService.class);

        logAppender = new ListAppender<>();
        logAppender.start();
        habitServiceLogger.addAppender(logAppender);
    }

    @AfterEach
    void detachHabitServiceLogAppender() {
        habitServiceLogger.detachAppender(logAppender);
        logAppender.stop();
    }

    @Test
    void createUsesMyBatisWritePath() {
        Instant createdAt =
            Instant.parse("2026-01-15T12:00:00Z");

        when(clock.instant()).thenReturn(createdAt);

        EnumSet<DayOfWeek> scheduledDays = EnumSet.of(
            DayOfWeek.MONDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.FRIDAY
        );

        when(habitWriteRepository.save(any(Habit.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        Habit created =
            habitCommandService.create(
               OWNER_ID,
                "Exercise",
                scheduledDays
            );

        assertThat(created.getName())
            .isEqualTo("Exercise");

        assertThat(created.getScheduledDays())
            .isEqualTo(scheduledDays);

        assertThat(created.getCreatedAt())
            .isEqualTo(createdAt);

        verify(habitWriteRepository)
            .save(same(created));
    }

    @Test
    void updateUsesMyBatisReadAndWritePath() {
        Long habitId = 42L;
        Long version = 3L;

        Habit existingHabit =
            new Habit("Old name", FIXED);

        existingHabit.synchronizePersistenceVersion(
            version
        );

        EnumSet<DayOfWeek> newScheduledDays =
            EnumSet.of(
                DayOfWeek.TUESDAY,
                DayOfWeek.THURSDAY
            );

        when(habitMapper.findById(
                OWNER_ID,
                habitId))
            .thenReturn(existingHabit);

        when(
            habitWriteRepository.save(
                same(existingHabit)
            )
        ).thenReturn(existingHabit);

        Habit updated =
            habitCommandService.update(
               OWNER_ID,
                habitId,
                version,
                "New name",
                newScheduledDays
            );

        assertThat(updated.getName())
            .isEqualTo("New name");

        assertThat(updated.getScheduledDays())
            .isEqualTo(newScheduledDays);

        verify(habitMapper)
            .findById(OWNER_ID, habitId);

        verify(habitWriteRepository)
            .save(same(existingHabit));

        verify(applicationEventPublisher)
            .publishEvent(
                any(DashboardChangedEvent.class)
            );
    }

    @Test
    void updateRejectsStaleClientVersionBeforeWriting() {
        Long habitId = 42L;

        Habit existingHabit =
            new Habit("Original name", FIXED);

        existingHabit.synchronizePersistenceVersion(
            4L
        );

        when(habitMapper.findById(
                OWNER_ID,
                habitId))
            .thenReturn(existingHabit);

        assertThatThrownBy(
            () -> habitCommandService.update(
               OWNER_ID,
                habitId,
                3L,
                "Changed name",
                null
            )
        )
            .isInstanceOf(
                HabitVersionConflictException.class
            )
            .hasMessage(
                "Habit version conflict: " + habitId
            );

        assertThat(existingHabit.getName())
            .isEqualTo("Original name");

        verify(habitMapper)
            .findById(OWNER_ID, habitId);

        verify(
            habitWriteRepository,
            never()
        ).save(any(Habit.class));

        verify(
            applicationEventPublisher,
            never()
        ).publishEvent(
            any(DashboardChangedEvent.class)
        );
    }

    @Test
    void archiveUsesMyBatisReadAndWritePath() {
        Long habitId = 42L;

        Habit existingHabit =
            new Habit("Read", FIXED);

        existingHabit.synchronizePersistenceVersion(
            2L
        );

        when(habitMapper.findById(
                OWNER_ID,
                habitId))
            .thenReturn(existingHabit);

        when(
            habitWriteRepository.save(
                same(existingHabit)
            )
        ).thenReturn(existingHabit);

        Habit archived =
            habitCommandService.archive(
               OWNER_ID,
                habitId);

        assertThat(archived.isArchived())
            .isTrue();

        verify(habitMapper)
            .findById(OWNER_ID, habitId);

        verify(habitWriteRepository)
            .save(same(existingHabit));

        verify(applicationEventPublisher)
            .publishEvent(
                any(DashboardChangedEvent.class)
            );
    }

    @Test
    void unarchiveUsesMyBatisReadAndWritePath() {
        Long habitId = 42L;

        Habit existingHabit =
            new Habit("Read", FIXED);

        existingHabit.archive();

        existingHabit.synchronizePersistenceVersion(
            2L
        );

        when(habitMapper.findById(
                OWNER_ID,
                habitId))
            .thenReturn(existingHabit);

        when(
            habitWriteRepository.save(
                same(existingHabit)
            )
        ).thenReturn(existingHabit);

        Habit unarchived =
            habitCommandService.unarchive(
               OWNER_ID,
                habitId);

        assertThat(unarchived.isArchived())
            .isFalse();

        verify(habitMapper)
            .findById(OWNER_ID, habitId);

        verify(habitWriteRepository)
            .save(same(existingHabit));

        verify(applicationEventPublisher)
            .publishEvent(
                any(DashboardChangedEvent.class)
            );
    }

    @Test
    void completeDoesNotWriteOrIncrementVersionWhenAlreadyCompletedToday() {
        Long habitId = 42L;

        LocalDate today =
            LocalDate.of(2024, 1, 5);

        Habit existingHabit =
            new Habit("Read", FIXED);

        existingHabit.complete(
            today,
            TEST_ZONE
        );

        existingHabit.synchronizePersistenceVersion(
            2L
        );

        when(habitMapper.findById(
                OWNER_ID,
                habitId))
            .thenReturn(existingHabit);

        Habit result =
            habitCommandService.complete(
               OWNER_ID,
                habitId,
                today
            );

        assertThat(result)
            .isSameAs(existingHabit);

        assertThat(result.getCompletionCount())
            .isEqualTo(1);

        assertThat(result.getVersion())
            .isEqualTo(2L);

        verify(habitMapper)
            .findById(OWNER_ID, habitId);

        verify(
            habitWriteRepository,
            never()
        ).save(any(Habit.class));

        verify(
            applicationEventPublisher,
            never()
        ).publishEvent(
            any(DashboardChangedEvent.class)
        );
    }

    @Test
    void completeUsesMyBatisReadAndWritePathWhenStateChanges() {
        Long habitId = 42L;

        LocalDate today =
            LocalDate.of(2024, 1, 5);

        Habit existingHabit =
            new Habit("Read", FIXED);

        existingHabit.synchronizePersistenceVersion(
            2L
        );

        when(habitMapper.findById(
                OWNER_ID,
                habitId))
            .thenReturn(existingHabit);

        when(
            habitWriteRepository.save(
                same(existingHabit)
            )
        ).thenReturn(existingHabit);

        Habit completed =
            habitCommandService.complete(
               OWNER_ID,
                habitId,
                today
            );

        assertThat(completed.getCompletionCount())
            .isEqualTo(1);

        assertThat(completed.getCurrentStreak())
            .isEqualTo(1);

        verify(habitMapper)
            .findById(OWNER_ID, habitId);

        verify(completionRepository)
            .save(any(HabitCompletion.class));

        verify(habitWriteRepository)
            .save(same(existingHabit));

        verify(applicationEventPublisher)
            .publishEvent(
                any(DashboardChangedEvent.class)
            );
    }

    @Test
    void completeStoresLastCompletionInBusinessClockZone() {
        Long habitId = 42L;

        LocalDate today =
            LocalDate.of(2026, 8, 3);

        ZoneId businessZone =
            ZoneId.of("Pacific/Kiritimati");

        Habit existingHabit =
            new Habit("Read", FIXED);

        existingHabit.synchronizePersistenceVersion(
            2L
        );

        when(clock.getZone())
            .thenReturn(businessZone);

        when(habitMapper.findById(
                OWNER_ID,
                habitId))
            .thenReturn(existingHabit);

        when(
            habitWriteRepository.save(
                same(existingHabit)
            )
        ).thenReturn(existingHabit);

        Habit completed =
            habitCommandService.complete(
               OWNER_ID,
                habitId,
                today
            );

        assertThat(completed.getLastCompletedAt())
            .isEqualTo(
                Instant.parse(
                    "2026-08-02T10:00:00Z"
                )
            );

        assertThat(
            completed.wasCompletedOn(
                today,
                businessZone
            )
        ).isTrue();

        assertThat(
            completed.effectiveCurrentStreak(
                today,
                businessZone
            )
        ).isEqualTo(1);

        verify(
            clock,
            atLeastOnce()
        ).getZone();
    }

    @Test
    void bulkCompletePublishesDashboardChangePerCompletedHabit() {
        LocalDate today =
            LocalDate.of(2024, 1, 5);

        Habit firstHabit =
            new Habit("Read", FIXED);

        firstHabit.synchronizePersistenceVersion(
            1L
        );

        Habit secondHabit =
            new Habit("Exercise", FIXED);

        secondHabit.synchronizePersistenceVersion(
            2L
        );

        when(habitMapper.findById(
                OWNER_ID,
                41L))
            .thenReturn(firstHabit);

        when(habitMapper.findById(
                OWNER_ID,
                42L))
            .thenReturn(secondHabit);

        habitCommandService.bulkComplete(
               OWNER_ID,
                List.of(41L, 42L),
            today
        );

        verify(habitWriteRepository)
            .save(same(firstHabit));

        verify(habitWriteRepository)
            .save(same(secondHabit));

        verify(
            applicationEventPublisher,
            times(2)
        ).publishEvent(
            any(DashboardChangedEvent.class)
        );
    }

    @Test
    void bulkCompleteDoesNotPublishDashboardChangeWhenNothingChanges() {
        Long habitId = 42L;

        LocalDate today =
            LocalDate.of(2024, 1, 5);

        Habit alreadyCompleted =
            new Habit("Read", FIXED);

        alreadyCompleted.complete(
            today,
            TEST_ZONE
        );

        alreadyCompleted.synchronizePersistenceVersion(
            2L
        );

        when(habitMapper.findById(
                OWNER_ID,
                habitId))
            .thenReturn(alreadyCompleted);

        habitCommandService.bulkComplete(
               OWNER_ID,
                List.of(habitId),
            today
        );

        verify(
            habitWriteRepository,
            never()
        ).save(any(Habit.class));

        verify(
            applicationEventPublisher,
            never()
        ).publishEvent(
            any(DashboardChangedEvent.class)
        );
    }

    @Test
    void bulkCompleteRetriesConflictedItemOnceAndReportsItAsCompleted() {
        Long habitId = 42L;

        LocalDate today =
            LocalDate.of(2024, 1, 5);

        Habit firstSnapshot =
            new Habit("Read", FIXED);

        firstSnapshot.synchronizePersistenceVersion(
            0L
        );

        Habit retrySnapshot =
            new Habit("Read", FIXED);

        retrySnapshot.synchronizePersistenceVersion(
            1L
        );

        when(habitMapper.findById(
                OWNER_ID,
                habitId))
            .thenReturn(
                firstSnapshot,
                retrySnapshot
            );

        when(
            habitWriteRepository.save(
                same(firstSnapshot)
            )
        ).thenThrow(
            new HabitVersionConflictException(
                habitId
            )
        );

        when(
            habitWriteRepository.save(
                same(retrySnapshot)
            )
        ).thenReturn(retrySnapshot);

        var response =
            habitCommandService.bulkComplete(
               OWNER_ID,
                List.of(habitId),
                today
            );

        assertThat(response.completed())
            .containsExactly(habitId);

        assertThat(response.conflicted())
            .isEmpty();

        verify(
            habitMapper,
            times(2)
        ).findById(OWNER_ID, habitId);

        verify(habitWriteRepository)
            .save(same(firstSnapshot));

        verify(habitWriteRepository)
            .save(same(retrySnapshot));

        verify(completionRepository)
            .save(any(HabitCompletion.class));

        verify(applicationEventPublisher)
            .publishEvent(
                any(DashboardChangedEvent.class)
            );

        assertThat(logAppender.list)
            .anySatisfy(logEvent -> {
                assertThat(logEvent.getLevel())
                    .isEqualTo(Level.INFO);

                assertThat(
                    logEvent.getFormattedMessage()
                ).isEqualTo(
                    "Bulk habit completion version conflict; "
                        + "retrying once, habitId: 42, "
                        + "date: 2024-01-05, reason: "
                        + "Habit version conflict: 42"
                );
            });
    }

    @Test
    void bulkCompleteReportsExhaustedConflictAndContinuesWithNextItem() {
        Long conflictedHabitId = 41L;
        Long completedHabitId = 42L;

        LocalDate today =
            LocalDate.of(2024, 1, 5);

        Habit firstConflictedSnapshot =
            new Habit("Read", FIXED);

        firstConflictedSnapshot
            .synchronizePersistenceVersion(0L);

        Habit retryConflictedSnapshot =
            new Habit("Read", FIXED);

        retryConflictedSnapshot
            .synchronizePersistenceVersion(1L);

        Habit completedHabit =
            new Habit("Exercise", FIXED);

        completedHabit.synchronizePersistenceVersion(
            0L
        );

        when(
            habitMapper.findById(
                OWNER_ID,
                conflictedHabitId
            )
        ).thenReturn(
            firstConflictedSnapshot,
            retryConflictedSnapshot
        );

        when(
            habitMapper.findById(
                OWNER_ID,
                completedHabitId
            )
        ).thenReturn(completedHabit);

        when(
            habitWriteRepository.save(
                same(firstConflictedSnapshot)
            )
        ).thenThrow(
            new HabitVersionConflictException(
                conflictedHabitId
            )
        );

        when(
            habitWriteRepository.save(
                same(retryConflictedSnapshot)
            )
        ).thenThrow(
            new HabitVersionConflictException(
                conflictedHabitId
            )
        );

        when(
            habitWriteRepository.save(
                same(completedHabit)
            )
        ).thenReturn(completedHabit);

        var response =
            habitCommandService.bulkComplete(
               OWNER_ID,
                List.of(
                    conflictedHabitId,
                    completedHabitId
                ),
                today
            );

        assertThat(response.conflicted())
            .containsExactly(conflictedHabitId);

        assertThat(response.completed())
            .containsExactly(completedHabitId);

        assertThat(response.skipped())
            .isEmpty();

        assertThat(response.failed())
            .isEmpty();

        assertThat(response.notFound())
            .isEmpty();

        verify(
            habitMapper,
            times(2)
        ).findById(OWNER_ID, conflictedHabitId);

        verify(habitMapper)
            .findById(OWNER_ID, completedHabitId);

        verify(
            completionRepository,
            times(1)
        ).save(any(HabitCompletion.class));

        verify(
            applicationEventPublisher,
            times(1)
        ).publishEvent(
            any(DashboardChangedEvent.class)
        );

        assertThat(logAppender.list)
            .anySatisfy(logEvent -> {
                assertThat(logEvent.getLevel())
                    .isEqualTo(Level.WARN);

                assertThat(
                    logEvent.getFormattedMessage()
                ).isEqualTo(
                    "Bulk habit completion retry exhausted, "
                        + "habitId: 41, "
                        + "date: 2024-01-05, "
                        + "reason: "
                        + "Habit version conflict: 41"
                );
            });
    }

    @Test
    void bulkCompleteDoesNotSwallowUnexpectedException() {
        Long completedHabitId = 41L;
        Long brokenHabitId = 42L;

        LocalDate today =
            LocalDate.of(2024, 1, 5);

        Habit completedHabit =
            new Habit("Read", FIXED);

        completedHabit.synchronizePersistenceVersion(
            0L
        );

        when(
            habitMapper.findById(
                OWNER_ID,
                completedHabitId
            )
        ).thenReturn(completedHabit);

        when(
            habitMapper.findById(
                OWNER_ID,
                brokenHabitId
            )
        ).thenThrow(
            new IllegalStateException(
                "Unexpected database failure"
            )
        );

        assertThatThrownBy(
            () -> habitCommandService.bulkComplete(
               OWNER_ID,
                List.of(
                    completedHabitId,
                    brokenHabitId
                ),
                today
            )
        )
            .isInstanceOf(
                IllegalStateException.class
            )
            .hasMessage(
                "Unexpected database failure"
            );

        verify(habitWriteRepository)
            .save(same(completedHabit));

        verify(completionRepository)
            .save(any(HabitCompletion.class));

        verify(applicationEventPublisher)
            .publishEvent(
                any(DashboardChangedEvent.class)
            );
    }

    @Test
    void bulkCompleteFailsWhenTransactionReturnsNoOutcome() {
        Long habitId = 42L;

        LocalDate today =
            LocalDate.of(2024, 1, 5);

        doReturn(null)
            .when(transactionTemplate)
            .execute(any());

        assertThatThrownBy(
            () -> habitCommandService
                .bulkComplete(
                    OWNER_ID, List.of(habitId),
                    today
                )
        )
            .isInstanceOf(
                NullPointerException.class
            )
            .hasMessage(
                "Bulk completion transaction "
                    + "must return an outcome"
            );

        verifyNoInteractions(
            habitMapper,
            habitWriteRepository,
            completionRepository,
            applicationEventPublisher
        );
    }

    @Test
    void uncompleteUsesMyBatisReadAndWritePath() {
        Long habitId = 42L;

        LocalDate today =
            LocalDate.of(2024, 1, 5);

        Habit existingHabit =
            new Habit("Read", FIXED);

        existingHabit.complete(
            today,
            TEST_ZONE
        );

        existingHabit.synchronizePersistenceVersion(
            2L
        );

        when(habitMapper.findById(
                OWNER_ID,
                habitId))
            .thenReturn(existingHabit);

        when(
            completionRepository
                .findByHabitIdOrderByCompletedOnDesc(
                    habitId
                )
        ).thenReturn(List.of());

        when(
            habitWriteRepository.save(
                same(existingHabit)
            )
        ).thenReturn(existingHabit);

        Habit uncompleted =
            habitCommandService.uncomplete(
               OWNER_ID,
                habitId,
                today
            );

        assertThat(
            uncompleted.getCompletionCount()
        ).isZero();

        assertThat(
            uncompleted.getLastCompletedAt()
        ).isNull();

        verify(habitMapper)
            .findById(OWNER_ID, habitId);

        verify(completionRepository)
            .deleteByHabitIdAndCompletedOn(
                habitId,
                today
            );

        verify(completionRepository)
            .findByHabitIdOrderByCompletedOnDesc(
                habitId
            );

        verify(habitWriteRepository)
            .save(same(existingHabit));

        verify(applicationEventPublisher)
            .publishEvent(
                any(DashboardChangedEvent.class)
            );
    }

    @Test
    void deleteUsesMyBatisExistenceCheckAndDelete() {
        Long habitId = 42L;

        when(habitMapper.existsById(OWNER_ID, habitId))
            .thenReturn(true);

        when(habitMapper.deleteById(OWNER_ID, habitId))
            .thenReturn(1);

        habitCommandService.delete(
               OWNER_ID,
                habitId);

        verify(habitMapper)
            .existsById(OWNER_ID, habitId);

        verify(habitMapper)
            .deleteById(OWNER_ID, habitId);

        verify(applicationEventPublisher)
            .publishEvent(
                any(DashboardChangedEvent.class)
            );
    }

    @Test
    void deleteThrowsNotFoundAndDoesNotDeleteWhenHabitDoesNotExist() {
        Long habitId = 42L;

        when(habitMapper.existsById(OWNER_ID, habitId))
            .thenReturn(false);

        assertThatThrownBy(
            () ->
                habitCommandService.delete(
               OWNER_ID,
                habitId
                )
        )
            .isInstanceOf(
                HabitNotFoundException.class
            )
            .hasMessage(
                "Habit not found: " + habitId
            );

        verify(habitMapper)
            .existsById(OWNER_ID, habitId);

        verify(
            habitMapper,
            never()
        ).deleteById(OWNER_ID, habitId);

        verify(
            applicationEventPublisher,
            never()
        ).publishEvent(
            any(DashboardChangedEvent.class)
        );
    }

    @Test
    void completeReturnsFreshStateWhenRetryFindsHabitAlreadyCompletedToday() {
        Long habitId = 42L;

        LocalDate today =
            LocalDate.of(2024, 1, 5);

        Habit staleHabit =
            new Habit("Read", FIXED);

        staleHabit.synchronizePersistenceVersion(
            0L
        );

        Habit freshlyCompletedHabit =
            new Habit("Read", FIXED);

        freshlyCompletedHabit.complete(
            today,
            TEST_ZONE
        );

        freshlyCompletedHabit
            .synchronizePersistenceVersion(1L);

        when(habitMapper.findById(
                OWNER_ID,
                habitId))
            .thenReturn(
                staleHabit,
                freshlyCompletedHabit
            );

        when(
            habitWriteRepository.save(
                same(staleHabit)
            )
        ).thenThrow(
            new HabitVersionConflictException(
                habitId
            )
        );

        Habit result =
            habitCommandService.complete(
               OWNER_ID,
                habitId,
                today
            );

        assertThat(result)
            .isSameAs(freshlyCompletedHabit);

        assertThat(result.getCompletionCount())
            .isEqualTo(1);

        assertThat(result.getVersion())
            .isEqualTo(1L);

        verify(
            habitMapper,
            times(2)
        ).findById(OWNER_ID, habitId);

        verify(habitWriteRepository)
            .save(same(staleHabit));

        verify(
            habitWriteRepository,
            never()
        ).save(same(freshlyCompletedHabit));

        verify(
            completionRepository,
            never()
        ).save(any(HabitCompletion.class));

        verify(
            applicationEventPublisher,
            never()
        ).publishEvent(
            any(DashboardChangedEvent.class)
        );

        assertThat(logAppender.list)
            .filteredOn(
                logEvent ->
                    logEvent
                        .getFormattedMessage()
                        .startsWith(
                            "Habit completion "
                                + "version conflict"
                        )
            )
            .singleElement()
            .satisfies(logEvent -> {
                assertThat(logEvent.getLevel())
                    .isEqualTo(Level.INFO);

                assertThat(
                    logEvent.getFormattedMessage()
                ).isEqualTo(
                    "Habit completion version conflict; "
                        + "retrying once, habitId: 42, "
                        + "date: 2024-01-05, reason: "
                        + "Habit version conflict: 42"
                );
            });

        assertThat(logAppender.list)
            .noneMatch(
                logEvent ->
                    logEvent
                        .getFormattedMessage()
                        .startsWith(
                            "Habit completion "
                                + "retry exhausted"
                        )
            );
    }

    @Test
    void completePropagatesConflictAfterSingleRetryIsExhausted() {
        Long habitId = 42L;

        LocalDate today =
            LocalDate.of(2024, 1, 5);

        Habit firstSnapshot =
            new Habit("Read", FIXED);

        firstSnapshot
            .synchronizePersistenceVersion(0L);

        Habit secondSnapshot =
            new Habit("Read", FIXED);

        secondSnapshot
            .synchronizePersistenceVersion(1L);

        when(habitMapper.findById(
                OWNER_ID,
                habitId))
            .thenReturn(
                firstSnapshot,
                secondSnapshot
            );

        when(
            habitWriteRepository.save(
                same(firstSnapshot)
            )
        ).thenThrow(
            new HabitVersionConflictException(
                habitId
            )
        );

        when(
            habitWriteRepository.save(
                same(secondSnapshot)
            )
        ).thenThrow(
            new HabitVersionConflictException(
                habitId
            )
        );

        assertThatThrownBy(
            () ->
                habitCommandService.complete(
               OWNER_ID,
                habitId,
                    today
                )
        )
            .isInstanceOf(
                HabitVersionConflictException.class
            )
            .hasMessage(
                "Habit version conflict: " + habitId
            );

        verify(
            habitMapper,
            times(2)
        ).findById(OWNER_ID, habitId);

        verify(habitWriteRepository)
            .save(same(firstSnapshot));

        verify(habitWriteRepository)
            .save(same(secondSnapshot));

        verify(
            completionRepository,
            never()
        ).save(any(HabitCompletion.class));

        verify(
            applicationEventPublisher,
            never()
        ).publishEvent(
            any(DashboardChangedEvent.class)
        );

        assertThat(logAppender.list)
            .filteredOn(
                logEvent ->
                    logEvent
                        .getFormattedMessage()
                        .startsWith(
                            "Habit completion"
                        )
            )
            .satisfiesExactly(
                firstLog -> {
                    assertThat(firstLog.getLevel())
                        .isEqualTo(Level.INFO);

                    assertThat(
                        firstLog.getFormattedMessage()
                    ).isEqualTo(
                        "Habit completion version conflict; "
                            + "retrying once, "
                            + "habitId: 42, "
                            + "date: 2024-01-05, "
                            + "reason: "
                            + "Habit version conflict: 42"
                    );
                },
                secondLog -> {
                    assertThat(secondLog.getLevel())
                        .isEqualTo(Level.WARN);

                    assertThat(
                        secondLog.getFormattedMessage()
                    ).isEqualTo(
                        "Habit completion retry exhausted, "
                            + "habitId: 42, "
                            + "date: 2024-01-05, "
                            + "reason: "
                            + "Habit version conflict: 42"
                    );
                }
            );
    }

    @Test
    void completeFailsLoudlyWhenTransactionReturnsNull() {
        Long habitId = 42L;

        LocalDate today =
            LocalDate.of(2024, 1, 5);

        doReturn(null)
            .when(transactionTemplate)
            .execute(any());

        assertThatThrownBy(
            () ->
                habitCommandService.complete(
               OWNER_ID,
                habitId,
                    today
                )
        )
            .isInstanceOf(
                NullPointerException.class
            )
            .hasMessage(
                "Completion transaction must "
                    + "return a Habit"
            );

        verifyNoInteractions(
            habitMapper,
            habitWriteRepository,
            completionRepository,
            applicationEventPublisher
        );
    }
}
