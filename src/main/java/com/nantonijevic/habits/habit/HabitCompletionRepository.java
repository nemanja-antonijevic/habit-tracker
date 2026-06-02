package com.nantonijevic.habits.habit;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HabitCompletionRepository extends JpaRepository<HabitCompletion, Long> {

    List<HabitCompletion> findByHabitIdOrderByCompletedOnDesc(Long habitId);
}
