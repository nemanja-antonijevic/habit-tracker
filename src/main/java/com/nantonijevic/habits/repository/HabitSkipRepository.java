package com.nantonijevic.habits.repository;

import com.nantonijevic.habits.domain.HabitSkip;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface HabitSkipRepository
    extends JpaRepository<HabitSkip, Long> {

    boolean existsByHabitIdAndMonthStart(
        Long habitId,
        LocalDate monthStart
    );

    boolean existsByHabitIdAndSkippedOn(
        Long habitId,
        LocalDate skippedOn
    );

    List<HabitSkip> findByHabitIdInAndSkippedOn(
        List<Long> habitIds,
        LocalDate skippedOn
    );

    List<HabitSkip>
    findByHabitIdAndSkippedOnBetween(
        Long habitId,
        LocalDate from,
        LocalDate to
    );

    List<HabitSkip> findByHabitIdInAndSkippedOnBetween(
        List<Long> habitIds,
        LocalDate from,
        LocalDate to
    );

    void deleteByHabitId(Long habitId);

    Optional<HabitSkip>
    findByHabitIdAndMonthStart(
        Long habitId,
        LocalDate monthStart
    );
}
