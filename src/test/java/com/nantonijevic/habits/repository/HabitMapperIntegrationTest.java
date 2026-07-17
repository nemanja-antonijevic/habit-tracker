package com.nantonijevic.habits.repository;

import com.nantonijevic.habits.AbstractIntegrationTest;
import com.nantonijevic.habits.domain.Habit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class HabitMapperIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private HabitMapper habitMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private HabitRepository habitRepository;

    @Test
    void findsHabitById() {
        jdbcTemplate.update("""
            INSERT INTO habits (id, name, scheduled_days, created_at)
            VALUES (?, ?, ?, CURRENT_TIMESTAMP)
            """, 42L, "Workout", "MONDAY,WEDNESDAY,FRIDAY");

        Habit habit = habitMapper.findById(42L);

        assertThat(habit).isNotNull();
        assertThat(habit.getId()).isEqualTo(42L);
        assertThat(habit.getVersion()).isZero();
        assertThat(habit.getName()).isEqualTo("Workout");
        assertThat(habit.getCompletionCount()).isZero();
        assertThat(habit.isArchived()).isFalse();
        assertThat(habit.getScheduledDays())
            .containsExactly(
                DayOfWeek.MONDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.FRIDAY
            );
    }

    @Test
    void searchesActiveHabitsByNameWithPagination() {
        jdbcTemplate.update("""
            INSERT INTO habits (id, name, archived, created_at)
            VALUES (?, ?, ?, CURRENT_TIMESTAMP)
            """, 101L, "Morning Run", false);

        jdbcTemplate.update("""
            INSERT INTO habits (id, name, archived, created_at)
            VALUES (?, ?, ?, CURRENT_TIMESTAMP)
            """, 102L, "Evening Run", false);

        jdbcTemplate.update("""
            INSERT INTO habits (id, name, archived, created_at)
            VALUES (?, ?, ?, CURRENT_TIMESTAMP)
            """, 103L, "Archived Run", true);

        Page<Habit> result = habitRepository.search(
            "RUN",
            false,
            PageRequest.of(
                0,
                1,
                Sort.by(Sort.Order.desc("id"))
            )
        );

        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getTotalPages()).isEqualTo(2);
        assertThat(result.getContent())
            .extracting(Habit::getName)
            .containsExactly("Evening Run");
    }

    @Test
    void deletesHabitByIdAndReturnsAffectedRows() {
        jdbcTemplate.update("""
            INSERT INTO habits (id, name, created_at)
            VALUES (?, ?, CURRENT_TIMESTAMP)
            """, 201L, "Delete me");

        assertThat(habitMapper.existsById(201L)).isTrue();

        int firstDeleteAffectedRows = habitMapper.deleteById(201L);
        int secondDeleteAffectedRows = habitMapper.deleteById(201L);

        assertThat(firstDeleteAffectedRows).isEqualTo(1);
        assertThat(secondDeleteAffectedRows).isZero();
        assertThat(habitMapper.existsById(201L)).isFalse();
    }
}
