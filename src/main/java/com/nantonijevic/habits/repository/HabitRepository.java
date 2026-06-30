package com.nantonijevic.habits.repository;

import com.nantonijevic.habits.domain.Habit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HabitRepository extends JpaRepository<Habit, Long> {

    @Query("""
        SELECT h
        FROM Habit h
        WHERE (:includeArchived = true OR h.archived = false)
          AND (:name IS NULL OR LOWER(h.name) LIKE LOWER(CONCAT('%', :name, '%')))
        """)
    Page<Habit> search(
            @Param("name") String name,
            @Param("includeArchived") boolean includeArchived,
            Pageable pageable
    );
}
