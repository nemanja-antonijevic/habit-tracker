package com.nantonijevic.habits.repository;

import com.nantonijevic.habits.domain.HabitCompletionStat;
import com.nantonijevic.habits.dto.HabitStatsView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface HabitCompletionStatRepository extends JpaRepository<HabitCompletionStat, Long> {
    @Query("""
      SELECT new com.nantonijevic.habits.dto.HabitStatsView(
          COUNT(s), MAX(s.currentStreak), MAX(s.completedOn), 0)
      FROM HabitCompletionStat s
      WHERE s.habitId = :habitId
      """)
    HabitStatsView findStatsByHabitId(@Param("habitId") Long habitId);

    void deleteByHabitIdAndCompletedOn(Long habitId, LocalDate completedOn);

    Optional<HabitCompletionStat> findFirstByHabitIdOrderByCompletedOnDesc(Long habitId);
}
