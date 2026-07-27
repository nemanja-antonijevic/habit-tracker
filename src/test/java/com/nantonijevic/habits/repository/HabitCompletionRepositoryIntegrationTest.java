package com.nantonijevic.habits.repository;

import com.nantonijevic.habits.AbstractIntegrationTest;
import com.nantonijevic.habits.domain.Habit;
import com.nantonijevic.habits.domain.HabitCompletion;
import com.nantonijevic.habits.support.HabitTestFixtureRepository;
import org.h2.jdbc.JdbcSQLIntegrityConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Transactional
class HabitCompletionRepositoryIntegrationTest
    extends AbstractIntegrationTest {

    @Autowired
    private HabitCompletionRepository completionRepository;

    @Autowired
    private HabitTestFixtureRepository habitFixtureRepository;

    @Test
    void rejectsDuplicateCompletionForSameHabitAndDate() {
        Habit habit = habitFixtureRepository.save(
            new Habit("Unique completion test")
        );
        LocalDate completedOn = LocalDate.of(2026, 7, 27);

        HabitCompletion firstCompletion =
            completionRepository.saveAndFlush(
                new HabitCompletion(habit.getId(), completedOn)
            );

        assertThat(firstCompletion.getId()).isNotNull();

        assertThatThrownBy(
            () -> completionRepository.saveAndFlush(
                new HabitCompletion(habit.getId(), completedOn)
            )
        )
            .isInstanceOf(DataIntegrityViolationException.class)
            .hasRootCauseInstanceOf(
                JdbcSQLIntegrityConstraintViolationException.class
            )
            .hasMessageContaining(
                "uq_completions_habit_completed"
            );
    }
}
