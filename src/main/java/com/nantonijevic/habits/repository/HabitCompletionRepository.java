package com.nantonijevic.habits.repository;

import com.nantonijevic.habits.domain.HabitCompletion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface HabitCompletionRepository extends JpaRepository<HabitCompletion, Long> {

    List<HabitCompletion> findByHabitIdOrderByCompletedOnDesc(Long habitId);

    void deleteByHabitIdAndCompletedOn(Long habitId, LocalDate completedOn);

    Optional<HabitCompletion> findFirstByHabitIdOrderByCompletedOnDesc(Long habitId);

    @Query("""
    select c
    from HabitCompletion c
    where c.habitId = :habitId
      and (:from is null or c.completedOn >= :from)
      and (:to is null or c.completedOn <= :to)
    order by c.completedOn desc
    """)
    Page<HabitCompletion> findByHabitIdAndCompletedOnBetweenOptional(
            @Param("habitId") Long habitId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            Pageable pageable);
}
