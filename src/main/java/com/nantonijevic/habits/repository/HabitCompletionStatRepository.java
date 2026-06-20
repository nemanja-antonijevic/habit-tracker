package com.nantonijevic.habits.repository;

import com.nantonijevic.habits.domain.HabitCompletionStat;
import com.nantonijevic.habits.dto.HabitStatsView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HabitCompletionStatRepository extends JpaRepository<HabitCompletionStat, Long> {
    @Query("""
      SELECT new com.nantonijevic.habits.dto.HabitStatsView(
          COUNT(s), MAX(s.currentStreak), MAX(s.completedOn))
      FROM HabitCompletionStat s
      WHERE s.habitId = :habitId
      """)
    HabitStatsView findStatsByHabitId(@Param("habitId") Long habitId);
}
