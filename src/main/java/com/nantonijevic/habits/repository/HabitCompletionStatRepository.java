package com.nantonijevic.habits.repository;

import com.nantonijevic.habits.domain.HabitCompletionStat;
import com.nantonijevic.habits.dto.HabitStatsView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface HabitCompletionStatRepository extends JpaRepository<HabitCompletionStat, Long> {
    @Query("""
      SELECT new com.nantonijevic.habits.dto.HabitStatsView(
          COUNT(s), MAX(s.currentStreak), MAX(s.completedOn), 0)
      FROM HabitCompletionStat s
      WHERE s.habitId = :habitId
      """)
    HabitStatsView findStatsByHabitId(@Param("habitId") Long habitId);

    @Query(
        value = """
        SELECT s.*
        FROM habit_completion_stats s
        JOIN (
            SELECT
                habit_id,
                MAX(completed_on) AS completed_on
            FROM habit_completion_stats
            WHERE habit_id IN (:habitIds)
            GROUP BY habit_id
        ) latest
            ON latest.habit_id = s.habit_id
            AND latest.completed_on = s.completed_on
        """,
        nativeQuery = true
    )
    List<HabitCompletionStat> findLatestByHabitIds(
        @Param("habitIds") Collection<Long> habitIds
    );

    @Query("""
    SELECT s.completedOn
    FROM HabitCompletionStat s
    WHERE s.habitId = :habitId
        AND s.completedOn BETWEEN :from AND :to
    ORDER BY s.completedOn
    """)
    List<LocalDate> findCompletedDatesInPeriod(
        @Param("habitId") Long habitId,
        @Param("from") LocalDate from,
        @Param("to") LocalDate to
    );

    void deleteByHabitIdAndCompletedOn(Long habitId, LocalDate completedOn);

    Optional<HabitCompletionStat> findFirstByHabitIdOrderByCompletedOnDesc(Long habitId);
}
