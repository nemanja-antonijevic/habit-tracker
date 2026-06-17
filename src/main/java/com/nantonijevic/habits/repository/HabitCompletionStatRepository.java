package com.nantonijevic.habits.repository;

import com.nantonijevic.habits.domain.HabitCompletionStat;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HabitCompletionStatRepository extends JpaRepository<HabitCompletionStat, Long> {

}
