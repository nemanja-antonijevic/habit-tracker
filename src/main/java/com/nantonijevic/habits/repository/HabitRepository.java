package com.nantonijevic.habits.repository;

import com.nantonijevic.habits.domain.Habit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HabitRepository extends JpaRepository<Habit, Long> {

    Page<Habit> findByArchivedFalse(Pageable pageable);
}
