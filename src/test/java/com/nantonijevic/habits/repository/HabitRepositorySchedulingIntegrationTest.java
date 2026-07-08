package com.nantonijevic.habits.repository;

import com.nantonijevic.habits.domain.Habit;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.DayOfWeek;
import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class HabitRepositorySchedulingIntegrationTest {

    @Autowired
    private HabitRepository habitRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void persistsScheduledDays() {
        Habit habit = new Habit("Workout");

        habit.setScheduledDays(EnumSet.of(
                DayOfWeek.MONDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.FRIDAY
        ));

        Habit saved = habitRepository.save(habit);

        entityManager.flush();
        entityManager.clear();

        Habit reloaded = habitRepository.findById(saved.getId()).orElseThrow();

        assertThat(reloaded.getScheduledDays())
                .containsExactlyInAnyOrder(
                        DayOfWeek.MONDAY,
                        DayOfWeek.WEDNESDAY,
                        DayOfWeek.FRIDAY
                );
    }
}
