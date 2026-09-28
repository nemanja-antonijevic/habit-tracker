package com.nantonijevic.habits.repository;

import com.nantonijevic.habits.AbstractIntegrationTest;
import com.nantonijevic.habits.domain.Habit;
import com.nantonijevic.habits.domain.HabitSkip;
import com.nantonijevic.habits.support.HabitTestFixtureRepository;
import com.nantonijevic.habits.support.TestApiClientOwner;
import org.h2.jdbc.JdbcSQLIntegrityConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Transactional
class HabitSkipRepositoryIntegrationTest
    extends AbstractIntegrationTest {

    private static final Long OWNER_ID = 502L;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private HabitSkipRepository skipRepository;

    @Autowired
    private HabitTestFixtureRepository
        habitFixtureRepository;

    @BeforeEach
    void ensureTestOwnerExists() {
        TestApiClientOwner.ensureExists(
            jdbcTemplate,
            OWNER_ID
        );
    }

    @Test
    void savesSkipWithCalendarMonthStart() {
        Habit habit = saveHabit();
        LocalDate skippedOn =
            LocalDate.of(2026, 7, 18);

        HabitSkip saved =
            skipRepository.saveAndFlush(
                new HabitSkip(
                    habit.getId(),
                    skippedOn
                )
            );

        assertThat(saved.getId())
            .isNotNull();
        assertThat(saved.skippedOn())
            .isEqualTo(skippedOn);
        assertThat(saved.monthStart())
            .isEqualTo(
                LocalDate.of(2026, 7, 1)
            );
    }

    @Test
    void rejectsSecondSkipInSameCalendarMonth() {
        Habit habit = saveHabit();

        skipRepository.saveAndFlush(
            new HabitSkip(
                habit.getId(),
                LocalDate.of(2026, 7, 8)
            )
        );

        assertThatThrownBy(
            () -> skipRepository.saveAndFlush(
                new HabitSkip(
                    habit.getId(),
                    LocalDate.of(2026, 7, 22)
                )
            )
        )
            .isInstanceOf(
                DataIntegrityViolationException.class
            )
            .hasRootCauseInstanceOf(
                JdbcSQLIntegrityConstraintViolationException.class
            )
            .hasMessageContaining(
                "uq_habit_skips_habit_month"
            );
    }

    private Habit saveHabit() {
        return habitFixtureRepository.save(
            new Habit(
                OWNER_ID,
                "Skip repository test",
                Instant.parse(
                    "2026-01-15T00:00:00Z"
                )
            )
        );
    }
}
