package com.nantonijevic.habits.service;

import com.nantonijevic.habits.domain.Habit;
import com.nantonijevic.habits.domain.HabitCompletion;
import com.nantonijevic.habits.domain.HabitNotFoundException;
import com.nantonijevic.habits.domain.HabitVersionConflictException;
import com.nantonijevic.habits.repository.HabitCompletionRepository;
import com.nantonijevic.habits.repository.HabitCompletionStatRepository;
import com.nantonijevic.habits.repository.HabitMapper;
import com.nantonijevic.habits.repository.HabitRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HabitServiceTest {

    @Mock
    private HabitRepository habitRepository;

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

        when(habitRepository.saveWithMyBatis(any(Habit.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        Habit created = habitService.create("Exercise", scheduledDays);

        assertThat(created.getName()).isEqualTo("Exercise");
        assertThat(created.getScheduledDays()).isEqualTo(scheduledDays);

        verify(habitRepository).saveWithMyBatis(same(created));
        verify(habitRepository, never()).save(any(Habit.class));
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
        when(habitRepository.saveWithMyBatis(same(existingHabit)))
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
        verify(habitRepository).saveWithMyBatis(same(existingHabit));

        verify(habitRepository, never()).findById(habitId);
        verify(habitRepository, never()).save(any(Habit.class));
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
        verify(habitRepository, never())
            .saveWithMyBatis(any(Habit.class));
    }

    @Test
    void archiveUsesMyBatisReadAndWritePath() {
        Long habitId = 42L;
        Habit existingHabit = new Habit("Read");
        existingHabit.synchronizePersistenceVersion(2L);

        when(habitMapper.findById(habitId)).thenReturn(existingHabit);
        when(habitRepository.saveWithMyBatis(same(existingHabit)))
            .thenReturn(existingHabit);

        Habit archived = habitService.archive(habitId);

        assertThat(archived.isArchived()).isTrue();

        verify(habitMapper).findById(habitId);
        verify(habitRepository).saveWithMyBatis(same(existingHabit));

        verify(habitRepository, never()).findById(habitId);
        verify(habitRepository, never()).save(any(Habit.class));
    }

    @Test
    void unarchiveUsesMyBatisReadAndWritePath() {
        Long habitId = 42L;
        Habit existingHabit = new Habit("Read");
        existingHabit.archive();
        existingHabit.synchronizePersistenceVersion(2L);

        when(habitMapper.findById(habitId)).thenReturn(existingHabit);
        when(habitRepository.saveWithMyBatis(same(existingHabit)))
            .thenReturn(existingHabit);

        Habit unarchived = habitService.unarchive(habitId);

        assertThat(unarchived.isArchived()).isFalse();

        verify(habitMapper).findById(habitId);
        verify(habitRepository).saveWithMyBatis(same(existingHabit));

        verify(habitRepository, never()).findById(habitId);
        verify(habitRepository, never()).save(any(Habit.class));
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
        verify(habitRepository, never())
            .saveWithMyBatis(any(Habit.class));
        verify(habitRepository, never())
            .save(any(Habit.class));
    }

    @Test
    void completeUsesMyBatisReadAndWritePathWhenStateChanges() {
        Long habitId = 42L;
        LocalDate today = LocalDate.of(2024, 1, 5);

        Habit existingHabit = new Habit("Read");
        existingHabit.synchronizePersistenceVersion(2L);

        when(habitMapper.findById(habitId)).thenReturn(existingHabit);
        when(habitRepository.saveWithMyBatis(same(existingHabit)))
            .thenReturn(existingHabit);

        Habit completed = habitService.complete(habitId, today);

        assertThat(completed.getCompletionCount()).isEqualTo(1);
        assertThat(completed.getCurrentStreak()).isEqualTo(1);

        verify(habitMapper).findById(habitId);
        verify(completionRepository).save(any(HabitCompletion.class));
        verify(habitRepository).saveWithMyBatis(same(existingHabit));

        verify(habitRepository, never()).findById(habitId);
        verify(habitRepository, never()).save(any(Habit.class));
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
        when(habitRepository.saveWithMyBatis(same(existingHabit)))
            .thenReturn(existingHabit);

        Habit uncompleted = habitService.uncomplete(habitId, today);

        assertThat(uncompleted.getCompletionCount()).isZero();
        assertThat(uncompleted.getLastCompletedAt()).isNull();

        verify(habitMapper).findById(habitId);
        verify(completionRepository)
            .deleteByHabitIdAndCompletedOn(habitId, today);
        verify(completionRepository)
            .findByHabitIdOrderByCompletedOnDesc(habitId);
        verify(habitRepository).saveWithMyBatis(same(existingHabit));

        verify(habitRepository, never()).findById(habitId);
        verify(habitRepository, never()).save(any(Habit.class));
    }

    @Test
    void deleteUsesMyBatisExistenceCheckAndDelete() {
        Long habitId = 42L;

        when(habitMapper.existsById(habitId)).thenReturn(true);
        when(habitMapper.deleteById(habitId)).thenReturn(1);

        habitService.delete(habitId);

        verify(habitMapper).existsById(habitId);
        verify(habitMapper).deleteById(habitId);

        verify(habitRepository, never()).findById(habitId);
        verify(habitRepository, never()).deleteById(habitId);
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

        verify(habitRepository, never()).findById(habitId);
        verify(habitRepository, never()).deleteById(habitId);
    }
}
