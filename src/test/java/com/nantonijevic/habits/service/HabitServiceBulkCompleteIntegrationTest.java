package com.nantonijevic.habits.service;

import com.nantonijevic.habits.AbstractIntegrationTest;
import com.nantonijevic.habits.domain.Habit;
import com.nantonijevic.habits.dto.BulkCompleteResponse;
import com.nantonijevic.habits.repository.HabitMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class HabitServiceBulkCompleteIntegrationTest
    extends AbstractIntegrationTest {

    @Autowired
    private HabitService habitService;

    @Autowired
    private HabitMapper habitMapper;

    @Test
    void bulkCompletePersistsMutatedHabitThroughMyBatis() {
        LocalDate today = LocalDate.of(2024, 1, 5);

        Habit habit = habitService.create(
            "Read",
            Set.of(today.getDayOfWeek())
        );

        BulkCompleteResponse response = habitService.bulkComplete(
            List.of(habit.getId()),
            today
        );

        Habit persisted = habitMapper.findById(habit.getId());

        assertThat(response.completed()).containsExactly(habit.getId());
        assertThat(persisted.getCompletionCount()).isEqualTo(1);
        assertThat(persisted.getCurrentStreak()).isEqualTo(1);
        assertThat(persisted.getLastCompletedAt()).isNotNull();
        assertThat(LocalDate.ofInstant(
            persisted.getLastCompletedAt(),
            ZoneId.systemDefault()
        )).isEqualTo(today);
        assertThat(persisted.getVersion()).isEqualTo(1L);
    }
}
