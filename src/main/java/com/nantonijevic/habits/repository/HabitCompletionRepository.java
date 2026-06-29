package com.nantonijevic.habits.repository;

import com.nantonijevic.habits.domain.HabitCompletion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface HabitCompletionRepository extends JpaRepository<HabitCompletion, Long> {

    List<HabitCompletion> findByHabitIdOrderByCompletedOnDesc(Long habitId);

    void deleteByHabitIdAndCompletedOn(Long habitId, LocalDate completedOn);

    Optional<HabitCompletion> findFirstByHabitIdOrderByCompletedOnDesc(Long habitId);

    Page<HabitCompletion> findByHabitIdOrderByCompletedOnDesc(Long habitId, Pageable pageable);
}
