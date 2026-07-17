package com.nantonijevic.habits.repository;

import com.nantonijevic.habits.AbstractIntegrationTest;
import com.nantonijevic.habits.domain.Habit;
import com.nantonijevic.habits.domain.HabitVersionConflictException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Transactional
class HabitWriteRepositoryIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private HabitWriteRepository habitRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private HabitMapper habitMapper;

    @Test
    void insertsHabitAndSynchronizesGeneratedIdAndVersion() {
        Habit habit = new Habit("Workout");

        habit.setScheduledDays(
            EnumSet.of(
                DayOfWeek.MONDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.FRIDAY
            )
        );

        Habit saved = habitRepository.saveWithMyBatis(habit);

        assertThat(saved).isSameAs(habit);
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getVersion()).isZero();

        Long rowCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM habits WHERE id = ?",
            Long.class,
            saved.getId()
        );

        String persistedName = jdbcTemplate.queryForObject(
            "SELECT name FROM habits WHERE id = ?",
            String.class,
            saved.getId()
        );

        Long persistedVersion = jdbcTemplate.queryForObject(
            "SELECT version FROM habits WHERE id = ?",
            Long.class,
            saved.getId()
        );

        String persistedScheduledDays = jdbcTemplate.queryForObject(
            "SELECT scheduled_days FROM habits WHERE id = ?",
            String.class,
            saved.getId()
        );

        assertThat(rowCount).isEqualTo(1L);
        assertThat(persistedName).isEqualTo("Workout");
        assertThat(persistedVersion).isZero();
        assertThat(persistedScheduledDays)
            .isEqualTo("MONDAY,WEDNESDAY,FRIDAY");
    }

    @Test
    void updatesHabitAndIncrementsVersionInDatabaseAndMemory() {
        Habit habit = new Habit("Workout");
        habitRepository.saveWithMyBatis(habit);

        assertThat(habit.getVersion()).isZero();

        habit.setName("Evening Workout");
        habit.setScheduledDays(
            EnumSet.of(
                DayOfWeek.TUESDAY,
                DayOfWeek.THURSDAY
            )
        );

        Habit updated = habitRepository.saveWithMyBatis(habit);

        assertThat(updated).isSameAs(habit);
        assertThat(updated.getVersion()).isEqualTo(1L);

        String persistedName = jdbcTemplate.queryForObject(
            "SELECT name FROM habits WHERE id = ?",
            String.class,
            habit.getId()
        );

        Long persistedVersion = jdbcTemplate.queryForObject(
            "SELECT version FROM habits WHERE id = ?",
            Long.class,
            habit.getId()
        );

        String persistedScheduledDays = jdbcTemplate.queryForObject(
            "SELECT scheduled_days FROM habits WHERE id = ?",
            String.class,
            habit.getId()
        );

        assertThat(persistedName).isEqualTo("Evening Workout");
        assertThat(persistedVersion).isEqualTo(1L);
        assertThat(persistedScheduledDays)
            .isEqualTo("TUESDAY,THURSDAY");
    }

    @Test
    void rejectsUpdateWhenVersionIsStale() {
        Habit habit = new Habit("Workout");
        habitRepository.saveWithMyBatis(habit);

        habit.setName("Current name");
        habitRepository.saveWithMyBatis(habit);

        assertThat(habit.getVersion()).isEqualTo(1L);

        Habit staleHabit = habitMapper.findById(habit.getId());

        staleHabit.setName("Stale name");
        staleHabit.synchronizePersistenceVersion(0L);

        assertThatThrownBy(
            () -> habitRepository.saveWithMyBatis(staleHabit)
        )
            .isInstanceOf(HabitVersionConflictException.class)
            .hasMessage("Habit version conflict: " + habit.getId());

        assertThat(staleHabit.getVersion()).isZero();

        String persistedName = jdbcTemplate.queryForObject(
            "SELECT name FROM habits WHERE id = ?",
            String.class,
            habit.getId()
        );

        Long persistedVersion = jdbcTemplate.queryForObject(
            "SELECT version FROM habits WHERE id = ?",
            Long.class,
            habit.getId()
        );

        assertThat(persistedName).isEqualTo("Current name");
        assertThat(persistedVersion).isEqualTo(1L);
    }
}
