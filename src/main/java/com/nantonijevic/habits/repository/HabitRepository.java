package com.nantonijevic.habits.repository;

import com.nantonijevic.habits.domain.Habit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HabitRepository
    extends JpaRepository<Habit, Long>,
    HabitSearchRepository,
    HabitWriteRepository {

    List<Habit> findByArchivedFalse();
}
