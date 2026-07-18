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

    @Query("""
    SELECT s
    FROM HabitCompletionStat s
    WHERE s.habitId IN :habitIds
      AND s.completedOn = (
          SELECT MAX(s2.completedOn)
          FROM HabitCompletionStat s2
          WHERE s2.habitId = s.habitId
      )
    """)
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
