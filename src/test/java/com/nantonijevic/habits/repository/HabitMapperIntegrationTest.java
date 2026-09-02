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
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class HabitMapperIntegrationTest extends AbstractIntegrationTest {

    private static final Long OWNER_ID = 501L;

    @org.junit.jupiter.api.BeforeEach
    void ensureTestOwnerExists() {
        com.nantonijevic.habits.support.TestApiClientOwner
            .ensureExists(jdbcTemplate, OWNER_ID);
    }

    @Autowired
    private HabitMapper habitMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private HabitSearchRepository habitSearchRepository;
    @Test
    void findsHabitById() {
        jdbcTemplate.update("""
            INSERT INTO habits (owner_id, id, name, scheduled_days, created_at)
            VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
            """, OWNER_ID, 42L, "Workout", "MONDAY,WEDNESDAY,FRIDAY");

        Habit habit = habitMapper.findById(OWNER_ID, 42L);

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
    void insertedOwnerRoundTripsThroughResultMap() {
        Long ownerId = OWNER_ID;

        Habit inserted = new Habit(
            ownerId,
            "Owned habit",
            Instant.parse("2026-09-02T06:00:00Z")
        );

        habitMapper.insert(inserted);

        Habit loaded =
            habitMapper.findById(
                ownerId,
                inserted.getId()
            );

        assertThat(loaded)
            .isNotNull();

        assertThat(loaded.getOwnerId())
            .isEqualTo(ownerId);

        assertThat(loaded.getName())
            .isEqualTo("Owned habit");
    }

    @Test
    void searchesActiveHabitsByNameWithPagination() {
        jdbcTemplate.update("""
            INSERT INTO habits (owner_id, id, name, archived, created_at)
            VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
            """, OWNER_ID, 101L, "Morning Run", false);

        jdbcTemplate.update("""
            INSERT INTO habits (owner_id, id, name, archived, created_at)
            VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
            """, OWNER_ID, 102L, "Evening Run", false);

        jdbcTemplate.update("""
            INSERT INTO habits (owner_id, id, name, archived, created_at)
            VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
            """, OWNER_ID, 103L, "Archived Run", true);

        Page<Habit> result = habitSearchRepository.search(
            OWNER_ID, "RUN",
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
            INSERT INTO habits (owner_id, id, name, created_at)
            VALUES (?, ?, ?, CURRENT_TIMESTAMP)
            """, OWNER_ID, 201L, "Delete me");

        assertThat(habitMapper.existsById(OWNER_ID, 201L)).isTrue();

        int firstDeleteAffectedRows = habitMapper.deleteById(OWNER_ID, 201L);
        int secondDeleteAffectedRows = habitMapper.deleteById(OWNER_ID, 201L);

        assertThat(firstDeleteAffectedRows).isEqualTo(1);
        assertThat(secondDeleteAffectedRows).isZero();
        assertThat(habitMapper.existsById(OWNER_ID, 201L)).isFalse();
    }
}
