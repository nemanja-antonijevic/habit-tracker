package com.nantonijevic.habits.repository;

import com.nantonijevic.habits.domain.Habit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface HabitSearchRepository {

    Page<Habit> search(
        Long ownerId,
        String name,
        boolean includeArchived,
        Pageable pageable
    );
}
