package com.nantonijevic.habits.service;

import com.nantonijevic.habits.domain.Habit;
import com.nantonijevic.habits.domain.HabitCompletion;
import com.nantonijevic.habits.domain.HabitNotFoundException;
import com.nantonijevic.habits.domain.HabitVersionConflictException;
import com.nantonijevic.habits.repository.HabitCompletionRepository;
import com.nantonijevic.habits.repository.HabitCompletionStatRepository;
import com.nantonijevic.habits.repository.HabitMapper;
import com.nantonijevic.habits.repository.HabitSearchRepository;
import com.nantonijevic.habits.repository.HabitWriteRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

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

    @InjectMocks
    private HabitService habitService;

    @Test
    void createUsesMyBatisWritePath() {
        EnumSet<DayOfWeek> scheduledDays = EnumSet.of(
            DayOfWeek.MONDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.FRIDAY
        );

        when(habitWriteRepository.saveWithMyBatis(any(Habit.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        Habit created = habitService.create("Exercise", scheduledDays);

        assertThat(created.getName()).isEqualTo("Exercise");
        assertThat(created.getScheduledDays()).isEqualTo(scheduledDays);

        verify(habitWriteRepository).saveWithMyBatis(same(created));
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
        when(habitWriteRepository.saveWithMyBatis(same(existingHabit)))
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
        verify(habitWriteRepository).saveWithMyBatis(same(existingHabit));
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
            .saveWithMyBatis(any(Habit.class));
    }

    @Test
    void archiveUsesMyBatisReadAndWritePath() {
        Long habitId = 42L;
        Habit existingHabit = new Habit("Read");
        existingHabit.synchronizePersistenceVersion(2L);

        when(habitMapper.findById(habitId)).thenReturn(existingHabit);
        when(habitWriteRepository.saveWithMyBatis(same(existingHabit)))
            .thenReturn(existingHabit);

        Habit archived = habitService.archive(habitId);

        assertThat(archived.isArchived()).isTrue();

        verify(habitMapper).findById(habitId);
        verify(habitWriteRepository).saveWithMyBatis(same(existingHabit));
    }

    @Test
    void unarchiveUsesMyBatisReadAndWritePath() {
        Long habitId = 42L;
        Habit existingHabit = new Habit("Read");
        existingHabit.archive();
        existingHabit.synchronizePersistenceVersion(2L);

        when(habitMapper.findById(habitId)).thenReturn(existingHabit);
        when(habitWriteRepository.saveWithMyBatis(same(existingHabit)))
            .thenReturn(existingHabit);

        Habit unarchived = habitService.unarchive(habitId);

        assertThat(unarchived.isArchived()).isFalse();

        verify(habitMapper).findById(habitId);
        verify(habitWriteRepository).saveWithMyBatis(same(existingHabit));
    }

    @Test
    void completeDoesNotWriteOrIncrementVersionWhenAlreadyCompletedToday() {
        Long habitId = 42L;
        LocalDate today = LocalDate.of(2024, 1, 5);

        Habit existingHabit = new Habit("Read");
        existingHabit.complete(today);
        existingHabit.synchronizePersistenceVersion(2L);

        when(habitMapper.findById(habitId)).thenReturn(existingHabit);

        Habit result = habitService.complete(habitId, today);

        assertThat(result).isSameAs(existingHabit);
        assertThat(result.getCompletionCount()).isEqualTo(1);
        assertThat(result.getVersion()).isEqualTo(2L);

        verify(habitMapper).findById(habitId);
        verify(habitWriteRepository, never())
            .saveWithMyBatis(any(Habit.class));
    }

    @Test
    void completeUsesMyBatisReadAndWritePathWhenStateChanges() {
        Long habitId = 42L;
        LocalDate today = LocalDate.of(2024, 1, 5);

        Habit existingHabit = new Habit("Read");
        existingHabit.synchronizePersistenceVersion(2L);

        when(habitMapper.findById(habitId)).thenReturn(existingHabit);
        when(habitWriteRepository.saveWithMyBatis(same(existingHabit)))
            .thenReturn(existingHabit);

        Habit completed = habitService.complete(habitId, today);

        assertThat(completed.getCompletionCount()).isEqualTo(1);
        assertThat(completed.getCurrentStreak()).isEqualTo(1);

        verify(habitMapper).findById(habitId);
        verify(completionRepository).save(any(HabitCompletion.class));
        verify(habitWriteRepository).saveWithMyBatis(same(existingHabit));
    }

    @Test
    void uncompleteUsesMyBatisReadAndWritePath() {
        Long habitId = 42L;
        LocalDate today = LocalDate.of(2024, 1, 5);

        Habit existingHabit = new Habit("Read");
        existingHabit.complete(today);
        existingHabit.synchronizePersistenceVersion(2L);

        when(habitMapper.findById(habitId)).thenReturn(existingHabit);
        when(completionRepository.findByHabitIdOrderByCompletedOnDesc(habitId))
            .thenReturn(List.of());
        when(habitWriteRepository.saveWithMyBatis(same(existingHabit)))
            .thenReturn(existingHabit);

        Habit uncompleted = habitService.uncomplete(habitId, today);

        assertThat(uncompleted.getCompletionCount()).isZero();
        assertThat(uncompleted.getLastCompletedAt()).isNull();

        verify(habitMapper).findById(habitId);
        verify(completionRepository)
            .deleteByHabitIdAndCompletedOn(habitId, today);
        verify(completionRepository)
            .findByHabitIdOrderByCompletedOnDesc(habitId);
        verify(habitWriteRepository).saveWithMyBatis(same(existingHabit));
    }

    @Test
    void deleteUsesMyBatisExistenceCheckAndDelete() {
        Long habitId = 42L;

        when(habitMapper.existsById(habitId)).thenReturn(true);
        when(habitMapper.deleteById(habitId)).thenReturn(1);

        habitService.delete(habitId);

        verify(habitMapper).existsById(habitId);
        verify(habitMapper).deleteById(habitId);
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
}
