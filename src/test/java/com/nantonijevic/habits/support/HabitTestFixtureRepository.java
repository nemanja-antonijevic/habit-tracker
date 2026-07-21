package com.nantonijevic.habits.support;

import com.nantonijevic.habits.domain.Habit;
import com.nantonijevic.habits.repository.HabitMapper;
import com.nantonijevic.habits.repository.HabitWriteRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class HabitTestFixtureRepository {

    private final HabitWriteRepository habitWriteRepository;
    private final HabitMapper habitMapper;
    private final JdbcTemplate jdbcTemplate;

    public HabitTestFixtureRepository(
        HabitWriteRepository habitWriteRepository,
        HabitMapper habitMapper,
        JdbcTemplate jdbcTemplate
    ) {
        this.habitWriteRepository = habitWriteRepository;
        this.habitMapper = habitMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    public Habit save(Habit habit) {
        return habitWriteRepository.save(habit);
    }

    public Optional<Habit> findById(Long habitId) {
        return Optional.ofNullable(habitMapper.findById(habitId));
    }

    public void deleteAll() {
        jdbcTemplate.update("DELETE FROM habits");
    }
}
