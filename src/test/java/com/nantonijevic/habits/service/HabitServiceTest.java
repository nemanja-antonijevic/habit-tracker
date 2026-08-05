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
import com.nantonijevic.habits.repository.HabitCompletionStatRepository;
import com.nantonijevic.habits.repository.HabitMapper;
import com.nantonijevic.habits.repository.HabitSearchRepository;
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
import org.springframework.test.util.ReflectionTestUtils;
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
class HabitServiceTest {

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
    private ApplicationEventPublisher applicationEventPublisher;

    @Mock
    private HabitCompletionStatRepository completionStatRepository;

    @Mock
    private TransactionTemplate transactionTemplate;

    @Mock
    private Clock clock;

    @InjectMocks
    private HabitService habitService;

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
            (Logger) LoggerFactory.getLogger(HabitService.class);

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

        Habit created = habitService.create("Exercise", scheduledDays);

        assertThat(created.getName()).isEqualTo("Exercise");
        assertThat(created.getScheduledDays()).isEqualTo(scheduledDays);
        assertThat(created.getCreatedAt()).isEqualTo(createdAt);

        verify(habitWriteRepository).save(same(created));
    }

    @Test
    void updateUsesMyBatisReadAndWritePath() {
        Long habitId = 42L;
        Long version = 3L;
        Habit existingHabit = new Habit("Old name");
        existingHabit.synchronizePersistenceVersion(version);

        EnumSet<DayOfWeek> newScheduledDays = EnumSet.of(
            DayOfWeek.TUESDAY,
            DayOfWeek.THURSDAY
        );

        when(habitMapper.findById(habitId)).thenReturn(existingHabit);
        when(habitWriteRepository.save(same(existingHabit)))
            .thenReturn(existingHabit);

        Habit updated = habitService.update(
            habitId,
            version,
            "New name",
            newScheduledDays
        );

        assertThat(updated.getName()).isEqualTo("New name");
        assertThat(updated.getScheduledDays()).isEqualTo(newScheduledDays);

        verify(habitMapper).findById(habitId);
        verify(habitWriteRepository).save(same(existingHabit));
        verify(applicationEventPublisher)
            .publishEvent(any(DashboardChangedEvent.class));
    }

    @Test
    void updateRejectsStaleClientVersionBeforeWriting() {
        Long habitId = 42L;
        Habit existingHabit = new Habit("Original name");
        existingHabit.synchronizePersistenceVersion(4L);

        when(habitMapper.findById(habitId)).thenReturn(existingHabit);

        assertThatThrownBy(() ->
            habitService.update(
                habitId,
                3L,
                "Changed name",
                null
            )
        )
            .isInstanceOf(HabitVersionConflictException.class)
            .hasMessage("Habit version conflict: " + habitId);

        assertThat(existingHabit.getName()).isEqualTo("Original name");

        verify(habitMapper).findById(habitId);
        verify(habitWriteRepository, never())
            .save(any(Habit.class));
        verify(applicationEventPublisher, never())
            .publishEvent(any(DashboardChangedEvent.class));
    }

    @Test
    void archiveUsesMyBatisReadAndWritePath() {
        Long habitId = 42L;
        Habit existingHabit = new Habit("Read");
        existingHabit.synchronizePersistenceVersion(2L);

        when(habitMapper.findById(habitId)).thenReturn(existingHabit);
        when(habitWriteRepository.save(same(existingHabit)))
            .thenReturn(existingHabit);

        Habit archived = habitService.archive(habitId);

        assertThat(archived.isArchived()).isTrue();

        verify(habitMapper).findById(habitId);
        verify(habitWriteRepository).save(same(existingHabit));
        verify(applicationEventPublisher)
            .publishEvent(any(DashboardChangedEvent.class));
    }

    @Test
    void unarchiveUsesMyBatisReadAndWritePath() {
        Long habitId = 42L;
        Habit existingHabit = new Habit("Read");
        existingHabit.archive();
        existingHabit.synchronizePersistenceVersion(2L);

        when(habitMapper.findById(habitId)).thenReturn(existingHabit);
        when(habitWriteRepository.save(same(existingHabit)))
            .thenReturn(existingHabit);

        Habit unarchived = habitService.unarchive(habitId);

        assertThat(unarchived.isArchived()).isFalse();

        verify(habitMapper).findById(habitId);
        verify(habitWriteRepository).save(same(existingHabit));
        verify(applicationEventPublisher)
            .publishEvent(any(DashboardChangedEvent.class));
    }

    @Test
    void completeDoesNotWriteOrIncrementVersionWhenAlreadyCompletedToday() {
        Long habitId = 42L;
        LocalDate today = LocalDate.of(2024, 1, 5);

        Habit existingHabit = new Habit("Read");
        existingHabit.complete(today, TEST_ZONE);
        existingHabit.synchronizePersistenceVersion(2L);

        when(habitMapper.findById(habitId)).thenReturn(existingHabit);

        Habit result = habitService.complete(habitId, today);

        assertThat(result).isSameAs(existingHabit);
        assertThat(result.getCompletionCount()).isEqualTo(1);
        assertThat(result.getVersion()).isEqualTo(2L);

        verify(habitMapper).findById(habitId);
        verify(habitWriteRepository, never())
            .save(any(Habit.class));
        verify(applicationEventPublisher, never())
            .publishEvent(any(DashboardChangedEvent.class));
    }

    @Test
    void completeUsesMyBatisReadAndWritePathWhenStateChanges() {
        Long habitId = 42L;
        LocalDate today = LocalDate.of(2024, 1, 5);

        Habit existingHabit = new Habit("Read");
        existingHabit.synchronizePersistenceVersion(2L);

        when(habitMapper.findById(habitId)).thenReturn(existingHabit);
        when(habitWriteRepository.save(same(existingHabit)))
            .thenReturn(existingHabit);

        Habit completed = habitService.complete(habitId, today);

        assertThat(completed.getCompletionCount()).isEqualTo(1);
        assertThat(completed.getCurrentStreak()).isEqualTo(1);

        verify(habitMapper).findById(habitId);
        verify(completionRepository).save(any(HabitCompletion.class));
        verify(habitWriteRepository).save(same(existingHabit));
        verify(applicationEventPublisher)
            .publishEvent(any(DashboardChangedEvent.class));
    }

    @Test
    void completeStoresLastCompletionInBusinessClockZone() {
        Long habitId = 42L;
        LocalDate today = LocalDate.of(2026, 8, 3);
        ZoneId businessZone =
            ZoneId.of("Pacific/Kiritimati");

        Habit existingHabit = new Habit("Read");
        existingHabit.synchronizePersistenceVersion(2L);

        when(clock.getZone()).thenReturn(businessZone);
        when(habitMapper.findById(habitId)).thenReturn(existingHabit);
        when(habitWriteRepository.save(same(existingHabit)))
            .thenReturn(existingHabit);

        Habit completed = habitService.complete(habitId, today);

        assertThat(completed.getLastCompletedAt())
            .isEqualTo(
                Instant.parse("2026-08-02T10:00:00Z")
            );
        assertThat(
            completed.wasCompletedOn(today, businessZone)
        ).isTrue();
        assertThat(
            completed.effectiveCurrentStreak(
                today,
                businessZone
            )
        ).isEqualTo(1);
        verify(clock).getZone();
    }

    @Test
    void bulkCompletePublishesDashboardChangePerCompletedHabit() {
        LocalDate today = LocalDate.of(2024, 1, 5);

        Habit firstHabit = new Habit("Read");
        firstHabit.synchronizePersistenceVersion(1L);

        Habit secondHabit = new Habit("Exercise");
        secondHabit.synchronizePersistenceVersion(2L);

        when(habitMapper.findById(41L)).thenReturn(firstHabit);
        when(habitMapper.findById(42L)).thenReturn(secondHabit);

        habitService.bulkComplete(List.of(41L, 42L), today);

        verify(habitWriteRepository).save(same(firstHabit));
        verify(habitWriteRepository).save(same(secondHabit));

        verify(applicationEventPublisher, times(2))
            .publishEvent(any(DashboardChangedEvent.class));
    }

    @Test
    void bulkCompleteDoesNotPublishDashboardChangeWhenNothingChanges() {
        Long habitId = 42L;
        LocalDate today = LocalDate.of(2024, 1, 5);

        Habit alreadyCompleted = new Habit("Read");
        alreadyCompleted.complete(today, TEST_ZONE);
        alreadyCompleted.synchronizePersistenceVersion(2L);

        when(habitMapper.findById(habitId)).thenReturn(alreadyCompleted);

        habitService.bulkComplete(List.of(habitId), today);

        verify(habitWriteRepository, never())
            .save(any(Habit.class));

        verify(applicationEventPublisher, never())
            .publishEvent(any(DashboardChangedEvent.class));
    }

    @Test
    void bulkCompleteRetriesConflictedItemOnceAndReportsItAsCompleted() {
        Long habitId = 42L;
        LocalDate today = LocalDate.of(2024, 1, 5);

        Habit firstSnapshot = new Habit("Read");
        firstSnapshot.synchronizePersistenceVersion(0L);

        Habit retrySnapshot = new Habit("Read");
        retrySnapshot.synchronizePersistenceVersion(1L);

        when(habitMapper.findById(habitId))
            .thenReturn(
                firstSnapshot,
                retrySnapshot
            );

        when(habitWriteRepository.save(same(firstSnapshot)))
            .thenThrow(
                new HabitVersionConflictException(habitId)
            );

        when(habitWriteRepository.save(same(retrySnapshot)))
            .thenReturn(retrySnapshot);

        var response =
            habitService.bulkComplete(List.of(habitId), today);

        assertThat(response.completed())
            .containsExactly(habitId);
        assertThat(response.conflicted()).isEmpty();

        verify(habitMapper, times(2))
            .findById(habitId);
        verify(habitWriteRepository)
            .save(same(firstSnapshot));
        verify(habitWriteRepository)
            .save(same(retrySnapshot));
        verify(completionRepository)
            .save(any(HabitCompletion.class));
        verify(applicationEventPublisher)
            .publishEvent(any(DashboardChangedEvent.class));

        assertThat(logAppender.list)
            .anySatisfy(logEvent -> {
                assertThat(logEvent.getLevel())
                    .isEqualTo(Level.INFO);
                assertThat(logEvent.getFormattedMessage())
                    .isEqualTo(
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
        LocalDate today = LocalDate.of(2024, 1, 5);

        Habit firstConflictedSnapshot = new Habit("Read");
        firstConflictedSnapshot.synchronizePersistenceVersion(0L);

        Habit retryConflictedSnapshot = new Habit("Read");
        retryConflictedSnapshot.synchronizePersistenceVersion(1L);

        Habit completedHabit = new Habit("Exercise");
        completedHabit.synchronizePersistenceVersion(0L);

        when(habitMapper.findById(conflictedHabitId))
            .thenReturn(
                firstConflictedSnapshot,
                retryConflictedSnapshot
            );

        when(habitMapper.findById(completedHabitId))
            .thenReturn(completedHabit);

        when(habitWriteRepository.save(
            same(firstConflictedSnapshot)
        )).thenThrow(
            new HabitVersionConflictException(conflictedHabitId)
        );

        when(habitWriteRepository.save(
            same(retryConflictedSnapshot)
        )).thenThrow(
            new HabitVersionConflictException(conflictedHabitId)
        );

        when(habitWriteRepository.save(same(completedHabit)))
            .thenReturn(completedHabit);

        var response = habitService.bulkComplete(
            List.of(conflictedHabitId, completedHabitId),
            today
        );

        assertThat(response.conflicted())
            .containsExactly(conflictedHabitId);
        assertThat(response.completed())
            .containsExactly(completedHabitId);
        assertThat(response.skipped()).isEmpty();
        assertThat(response.failed()).isEmpty();
        assertThat(response.notFound()).isEmpty();

        verify(habitMapper, times(2))
            .findById(conflictedHabitId);
        verify(habitMapper)
            .findById(completedHabitId);

        verify(completionRepository, times(1))
            .save(any(HabitCompletion.class));
        verify(applicationEventPublisher, times(1))
            .publishEvent(any(DashboardChangedEvent.class));

        assertThat(logAppender.list)
            .anySatisfy(logEvent -> {
                assertThat(logEvent.getLevel())
                    .isEqualTo(Level.WARN);
                assertThat(logEvent.getFormattedMessage())
                    .isEqualTo(
                        "Bulk habit completion retry exhausted, "
                            + "habitId: 41, date: 2024-01-05, "
                            + "reason: Habit version conflict: 41"
                    );
            });
    }

    @Test
    void bulkCompleteDoesNotSwallowUnexpectedException() {
        Long completedHabitId = 41L;
        Long brokenHabitId = 42L;
        LocalDate today = LocalDate.of(2024, 1, 5);

        Habit completedHabit = new Habit("Read");
        completedHabit.synchronizePersistenceVersion(0L);

        when(habitMapper.findById(completedHabitId))
            .thenReturn(completedHabit);

        when(habitMapper.findById(brokenHabitId))
            .thenThrow(
                new IllegalStateException("Unexpected database failure")
            );

        assertThatThrownBy(
            () -> habitService.bulkComplete(
                List.of(completedHabitId, brokenHabitId),
                today
            )
        )
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Unexpected database failure");

        verify(habitWriteRepository)
            .save(same(completedHabit));
        verify(completionRepository)
            .save(any(HabitCompletion.class));
        verify(applicationEventPublisher)
            .publishEvent(any(DashboardChangedEvent.class));
    }

    @Test
    void bulkCompleteFailsWhenTransactionReturnsNoOutcome() {
        Long habitId = 42L;
        LocalDate today = LocalDate.of(2024, 1, 5);

        doReturn(null)
            .when(transactionTemplate)
            .execute(any());

        assertThatThrownBy(
            () -> habitService.bulkComplete(
                List.of(habitId),
                today
            )
        )
            .isInstanceOf(NullPointerException.class)
            .hasMessage(
                "Bulk completion transaction must return an outcome"
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
        LocalDate today = LocalDate.of(2024, 1, 5);

        Habit existingHabit = new Habit("Read");
        existingHabit.complete(today, TEST_ZONE);
        existingHabit.synchronizePersistenceVersion(2L);

        when(habitMapper.findById(habitId)).thenReturn(existingHabit);
        when(completionRepository.findByHabitIdOrderByCompletedOnDesc(habitId))
            .thenReturn(List.of());
        when(habitWriteRepository.save(same(existingHabit)))
            .thenReturn(existingHabit);

        Habit uncompleted = habitService.uncomplete(habitId, today);

        assertThat(uncompleted.getCompletionCount()).isZero();
        assertThat(uncompleted.getLastCompletedAt()).isNull();

        verify(habitMapper).findById(habitId);
        verify(completionRepository)
            .deleteByHabitIdAndCompletedOn(habitId, today);
        verify(completionRepository)
            .findByHabitIdOrderByCompletedOnDesc(habitId);
        verify(habitWriteRepository).save(same(existingHabit));
        verify(applicationEventPublisher)
            .publishEvent(any(DashboardChangedEvent.class));
    }

    @Test
    void deleteUsesMyBatisExistenceCheckAndDelete() {
        Long habitId = 42L;

        when(habitMapper.existsById(habitId)).thenReturn(true);
        when(habitMapper.deleteById(habitId)).thenReturn(1);

        habitService.delete(habitId);

        verify(habitMapper).existsById(habitId);
        verify(habitMapper).deleteById(habitId);
        verify(applicationEventPublisher)
            .publishEvent(any(DashboardChangedEvent.class));
    }

    @Test
    void deleteThrowsNotFoundAndDoesNotDeleteWhenHabitDoesNotExist() {
        Long habitId = 42L;

        when(habitMapper.existsById(habitId)).thenReturn(false);

        assertThatThrownBy(() -> habitService.delete(habitId))
            .isInstanceOf(HabitNotFoundException.class)
            .hasMessage("Habit not found: " + habitId);

        verify(habitMapper).existsById(habitId);
        verify(habitMapper, never()).deleteById(habitId);
        verify(applicationEventPublisher, never())
            .publishEvent(any(DashboardChangedEvent.class));
    }

    @Test
    void completionRateRoundsOneThirdToFourDecimalPlaces() {
        Long habitId = 42L;
        Habit habit = new Habit("Read");

        LocalDate createdDate = LocalDate.ofInstant(
            habit.getCreatedAt(),
            ZoneId.systemDefault()
        );

        LocalDate from = createdDate.plusDays(1);
        LocalDate to = from.plusDays(2);

        habit.setScheduledDays(EnumSet.of(
            from.getDayOfWeek(),
            from.plusDays(1).getDayOfWeek(),
            to.getDayOfWeek()
        ));

        when(habitMapper.findById(habitId)).thenReturn(habit);
        when(completionStatRepository.findCompletedDatesInPeriod(
            habitId,
            from,
            to
        )).thenReturn(List.of(from));

        var response = habitService.getCompletionRate(
            habitId,
            from,
            to
        );

        assertThat(response.scheduled()).isEqualTo(3);
        assertThat(response.completed()).isEqualTo(1);
        assertThat(response.rate())
            .isEqualByComparingTo("0.3333");
    }

    @Test
    void completionRateReturnsEmptyResponseWithoutQueryWhenHabitWasCreatedAfterWindow() {
        Long habitId = 42L;
        Habit habit = new Habit("Read");

        LocalDate from = LocalDate.of(2000, 1, 1);
        LocalDate to = LocalDate.of(2000, 1, 31);

        when(habitMapper.findById(habitId)).thenReturn(habit);

        var response = habitService.getCompletionRate(
            habitId,
            from,
            to
        );

        assertThat(response.scheduled()).isZero();
        assertThat(response.completed()).isZero();
        assertThat(response.rate()).isNull();

        verifyNoInteractions(completionStatRepository);
    }

    @Test
    void completionRateStartsAtHabitCreationDateWhenHabitIsYoungerThanWindow() {
        Long habitId = 42L;
        Habit habit = new Habit("Read");

        Instant createdAt =
            Instant.parse("2024-01-03T12:00:00Z");

        ReflectionTestUtils.setField(
            habit,
            "createdAt",
            createdAt
        );

        LocalDate createdDate = LocalDate.ofInstant(
            createdAt,
            ZoneId.systemDefault()
        );

        LocalDate from = createdDate.minusDays(2);
        LocalDate to = createdDate.plusDays(2);

        habit.setScheduledDays(
            EnumSet.allOf(DayOfWeek.class)
        );

        when(habitMapper.findById(habitId))
            .thenReturn(habit);

        when(completionStatRepository.findCompletedDatesInPeriod(
            habitId,
            createdDate,
            to
        )).thenReturn(List.of(
            createdDate,
            createdDate.plusDays(1)
        ));

        var response = habitService.getCompletionRate(
            habitId,
            from,
            to
        );

        assertThat(response.scheduled()).isEqualTo(3);
        assertThat(response.completed()).isEqualTo(2);
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
        LocalDate from = LocalDate.of(2024, 1, 1);
        LocalDate to = LocalDate.of(2024, 1, 31);

        when(habitMapper.findById(habitId))
            .thenReturn(null);

        assertThatThrownBy(() ->
            habitService.getCompletionRate(
                habitId,
                from,
                to
            )
        )
            .isInstanceOf(HabitNotFoundException.class)
            .hasMessage("Habit not found: " + habitId);

        verifyNoInteractions(completionStatRepository);
    }

    @Test
    void completionRateExcludesCompletionsOnUnscheduledDays() {
        Long habitId = 42L;
        Habit habit = new Habit("Read");

        Instant createdAt =
            Instant.parse("2024-01-01T12:00:00Z");

        ReflectionTestUtils.setField(
            habit,
            "createdAt",
            createdAt
        );

        LocalDate scheduledDate = LocalDate.ofInstant(
            createdAt,
            ZoneId.systemDefault()
        );

        LocalDate offDay = scheduledDate.plusDays(1);

        habit.setScheduledDays(
            EnumSet.of(scheduledDate.getDayOfWeek())
        );

        when(habitMapper.findById(habitId))
            .thenReturn(habit);

        when(completionStatRepository.findCompletedDatesInPeriod(
            habitId,
            scheduledDate,
            offDay
        )).thenReturn(List.of(
            scheduledDate,
            offDay
        ));

        var response = habitService.getCompletionRate(
            habitId,
            scheduledDate,
            offDay
        );

        assertThat(response.scheduled()).isEqualTo(1);
        assertThat(response.completed()).isEqualTo(1);
        assertThat(response.rate())
            .isEqualByComparingTo("1.0000");
    }

    @Test
    void completionRateReturnsNullWhenWindowHasNoScheduledOccurrences() {
        Long habitId = 42L;
        Habit habit = new Habit("Read");

        LocalDate createdDate = LocalDate.ofInstant(
            habit.getCreatedAt(),
            ZoneId.systemDefault()
        );

        LocalDate from = createdDate.plusDays(1);
        LocalDate to = from.plusDays(1);

        DayOfWeek scheduledDayOutsideWindow =
            to.plusDays(1).getDayOfWeek();

        habit.setScheduledDays(
            EnumSet.of(scheduledDayOutsideWindow)
        );

        when(habitMapper.findById(habitId))
            .thenReturn(habit);

        when(completionStatRepository.findCompletedDatesInPeriod(
            habitId,
            from,
            to
        )).thenReturn(List.of());

        var response = habitService.getCompletionRate(
            habitId,
            from,
            to
        );

        assertThat(response.scheduled()).isZero();
        assertThat(response.completed()).isZero();
        assertThat(response.rate()).isNull();
    }

    @Test
    void completionRateReturnsZeroWhenSingleScheduledDayWasNotCompleted() {
        Long habitId = 42L;
        Habit habit = new Habit("Read");

        LocalDate createdDate = LocalDate.ofInstant(
            habit.getCreatedAt(),
            ZoneId.systemDefault()
        );

        LocalDate date = createdDate.plusDays(1);

        habit.setScheduledDays(
            EnumSet.of(date.getDayOfWeek())
        );

        when(habitMapper.findById(habitId))
            .thenReturn(habit);

        when(completionStatRepository.findCompletedDatesInPeriod(
            habitId,
            date,
            date
        )).thenReturn(List.of());

        var response = habitService.getCompletionRate(
            habitId,
            date,
            date
        );

        assertThat(response.scheduled()).isEqualTo(1);
        assertThat(response.completed()).isZero();
        assertThat(response.rate())
            .isEqualByComparingTo("0.0000");
    }

    @Test
    void completeReturnsFreshStateWhenRetryFindsHabitAlreadyCompletedToday() {
        Long habitId = 42L;
        LocalDate today = LocalDate.of(2024, 1, 5);

        Habit staleHabit = new Habit("Read");
        staleHabit.synchronizePersistenceVersion(0L);

        Habit freshlyCompletedHabit = new Habit("Read");
        freshlyCompletedHabit.complete(today, TEST_ZONE);
        freshlyCompletedHabit.synchronizePersistenceVersion(1L);

        when(habitMapper.findById(habitId))
            .thenReturn(
                staleHabit,
                freshlyCompletedHabit
            );

        when(habitWriteRepository.save(same(staleHabit)))
            .thenThrow(
                new HabitVersionConflictException(habitId)
            );

        Habit result = habitService.complete(habitId, today);

        assertThat(result).isSameAs(freshlyCompletedHabit);
        assertThat(result.getCompletionCount()).isEqualTo(1);
        assertThat(result.getVersion()).isEqualTo(1L);

        verify(habitMapper, times(2))
            .findById(habitId);
        verify(habitWriteRepository)
            .save(same(staleHabit));
        verify(habitWriteRepository, never())
            .save(same(freshlyCompletedHabit));
        verify(completionRepository, never())
            .save(any(HabitCompletion.class));
        verify(applicationEventPublisher, never())
            .publishEvent(any(DashboardChangedEvent.class));

        assertThat(logAppender.list)
            .filteredOn(
                logEvent ->
                    logEvent.getFormattedMessage()
                        .startsWith(
                            "Habit completion version conflict"
                        )
            )
            .singleElement()
            .satisfies(logEvent -> {
                assertThat(logEvent.getLevel())
                    .isEqualTo(Level.INFO);
                assertThat(logEvent.getFormattedMessage())
                    .isEqualTo(
                        "Habit completion version conflict; "
                            + "retrying once, habitId: 42, "
                            + "date: 2024-01-05, reason: "
                            + "Habit version conflict: 42"
                    );
            });

        assertThat(logAppender.list)
            .noneMatch(
                logEvent ->
                    logEvent.getFormattedMessage()
                        .startsWith(
                            "Habit completion retry exhausted"
                        )
            );
    }

    @Test
    void completePropagatesConflictAfterSingleRetryIsExhausted() {
        Long habitId = 42L;
        LocalDate today = LocalDate.of(2024, 1, 5);

        Habit firstSnapshot = new Habit("Read");
        firstSnapshot.synchronizePersistenceVersion(0L);

        Habit secondSnapshot = new Habit("Read");
        secondSnapshot.synchronizePersistenceVersion(1L);

        when(habitMapper.findById(habitId))
            .thenReturn(
                firstSnapshot,
                secondSnapshot
            );

        when(habitWriteRepository.save(same(firstSnapshot)))
            .thenThrow(
                new HabitVersionConflictException(habitId)
            );

        when(habitWriteRepository.save(same(secondSnapshot)))
            .thenThrow(
                new HabitVersionConflictException(habitId)
            );

        assertThatThrownBy(
            () -> habitService.complete(habitId, today)
        )
            .isInstanceOf(HabitVersionConflictException.class)
            .hasMessage("Habit version conflict: " + habitId);

        verify(habitMapper, times(2))
            .findById(habitId);
        verify(habitWriteRepository)
            .save(same(firstSnapshot));
        verify(habitWriteRepository)
            .save(same(secondSnapshot));
        verify(completionRepository, never())
            .save(any(HabitCompletion.class));
        verify(applicationEventPublisher, never())
            .publishEvent(any(DashboardChangedEvent.class));

        assertThat(logAppender.list)
            .filteredOn(
                logEvent ->
                    logEvent.getFormattedMessage()
                        .startsWith("Habit completion")
            )
            .satisfiesExactly(
                firstLog -> {
                    assertThat(firstLog.getLevel())
                        .isEqualTo(Level.INFO);
                    assertThat(firstLog.getFormattedMessage())
                        .isEqualTo(
                            "Habit completion version conflict; "
                                + "retrying once, habitId: 42, "
                                + "date: 2024-01-05, reason: "
                                + "Habit version conflict: 42"
                        );
                },
                secondLog -> {
                    assertThat(secondLog.getLevel())
                        .isEqualTo(Level.WARN);
                    assertThat(secondLog.getFormattedMessage())
                        .isEqualTo(
                            "Habit completion retry exhausted, "
                                + "habitId: 42, date: 2024-01-05, "
                                + "reason: Habit version conflict: 42"
                        );
                }
            );
    }

    @Test
    void completeFailsLoudlyWhenTransactionReturnsNull() {
        Long habitId = 42L;
        LocalDate today = LocalDate.of(2024, 1, 5);

        doReturn(null)
            .when(transactionTemplate)
            .execute(any());

        assertThatThrownBy(
            () -> habitService.complete(habitId, today)
        )
            .isInstanceOf(NullPointerException.class)
            .hasMessage(
                "Completion transaction must return a Habit"
            );

        verifyNoInteractions(
            habitMapper,
            habitWriteRepository,
            completionRepository,
            applicationEventPublisher
        );
    }

    @Test
    void completionRateIncludesBusinessDateOnWhichNewHabitWasCompleted() {
        Long habitId = 42L;
        // Must be earlier than the ambient clock so the old implementation
        // incorrectly clamps the entire completion-rate window away.
        LocalDate businessDate = LocalDate.of(2020, 1, 1);

        Instant businessInstant = businessDate
            .atTime(12, 0)
            .atZone(ZoneId.systemDefault())
            .toInstant();

        when(clock.instant()).thenReturn(businessInstant);

        when(habitWriteRepository.save(any(Habit.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        Habit habit = habitService.create(
            "Read",
            EnumSet.allOf(DayOfWeek.class)
        );

        when(habitMapper.findById(habitId))
            .thenReturn(habit);

        when(completionStatRepository.findCompletedDatesInPeriod(
            habitId,
            businessDate,
            businessDate
        )).thenReturn(List.of(businessDate));

        habitService.complete(habitId, businessDate);

        var response = habitService.getCompletionRate(
            habitId,
            businessDate,
            businessDate
        );

        assertThat(response.scheduled()).isEqualTo(1);
        assertThat(response.completed()).isEqualTo(1);
        assertThat(response.rate())
            .isEqualByComparingTo("1.0000");
    }

    @Test
    void dashboardDoesNotQueryStatsWhenThereAreNoActiveHabits() {
        LocalDate today = LocalDate.of(2026, 8, 1);

        when(habitMapper.findActive())
            .thenReturn(List.of());

        var dashboard = habitService.getDashboardStats(today);

        assertThat(dashboard.dueToday()).isZero();
        assertThat(dashboard.completedToday()).isZero();
        assertThat(dashboard.activeStreaks()).isZero();
        assertThat(dashboard.longestActiveStreak()).isZero();
        assertThat(dashboard.totalHabits()).isZero();

        verify(habitMapper).findActive();
        verifyNoInteractions(completionStatRepository);
    }
}
