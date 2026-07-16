package com.nantonijevic.habits.service;

import com.nantonijevic.habits.AbstractIntegrationTest;
import com.nantonijevic.habits.domain.Habit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class HabitServiceDueTodayIntegrationTest
    extends AbstractIntegrationTest {

    @Autowired
    private HabitService habitService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void dueTodayAndCountUseActiveMappedHabitsInStableOrder() {
        LocalDate today = LocalDate.of(2024, 1, 5);

        insertHabit(
            "Due older",
            "FRIDAY",
            false,
            null,
            LocalDateTime.of(2024, 1, 1, 9, 0)
        );

        insertHabit(
            "Due newer",
            "FRIDAY",
            false,
            null,
            LocalDateTime.of(2024, 1, 2, 9, 0)
        );

        insertHabit(
            "Not scheduled today",
            "SATURDAY",
            false,
            null,
            LocalDateTime.of(2024, 1, 3, 9, 0)
        );

        insertHabit(
            "Archived due",
            "FRIDAY",
            true,
            null,
            LocalDateTime.of(2024, 1, 4, 9, 0)
        );

        insertHabit(
            "Completed today",
            "FRIDAY",
            false,
            LocalDateTime.of(2024, 1, 5, 12, 0),
            LocalDateTime.of(2024, 1, 5, 9, 0)
        );

        Page<Habit> firstPage = habitService.dueToday(
            today,
            PageRequest.of(0, 1)
        );

        Page<Habit> secondPage = habitService.dueToday(
            today,
            PageRequest.of(1, 1)
        );

        long count = habitService.countDueToday(today);

        assertThat(firstPage.getContent())
            .extracting(Habit::getName)
            .containsExactly("Due newer");

        assertThat(firstPage.getTotalElements()).isEqualTo(2);
        assertThat(firstPage.getTotalPages()).isEqualTo(2);

        assertThat(secondPage.getContent())
            .extracting(Habit::getName)
            .containsExactly("Due older");

        assertThat(count).isEqualTo(2);
    }

    private void insertHabit(
        String name,
        String scheduledDays,
        boolean archived,
        LocalDateTime lastCompletedAt,
        LocalDateTime createdAt
    ) {
        Timestamp completedTimestamp = lastCompletedAt == null
            ? null
            : Timestamp.valueOf(lastCompletedAt);

        jdbcTemplate.update("""
            INSERT INTO habits (
                name,
                scheduled_days,
                archived,
                last_completed_at,
                created_at
            )
            VALUES (?, ?, ?, ?, ?)
            """,
            name,
            scheduledDays,
            archived,
            completedTimestamp,
            Timestamp.valueOf(createdAt)
        );
    }
}
