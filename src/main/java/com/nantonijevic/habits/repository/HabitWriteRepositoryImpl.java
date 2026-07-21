package com.nantonijevic.habits.repository;

import com.nantonijevic.habits.domain.Habit;
import com.nantonijevic.habits.domain.HabitVersionConflictException;
import org.springframework.stereotype.Repository;

@Repository
public class HabitWriteRepositoryImpl implements HabitWriteRepository {

    private final HabitMapper habitMapper;

    public HabitWriteRepositoryImpl(HabitMapper habitMapper) {
        this.habitMapper = habitMapper;
    }

    @Override
    public Habit save(Habit habit) {
        if (habit.getId() == null) {
            habitMapper.insert(habit);
            habit.synchronizePersistenceVersion(0L);
            return habit;
        }

        long previousVersion = habit.getVersion();
        int affectedRows = habitMapper.update(habit);

        if (affectedRows == 0) {
            throw new HabitVersionConflictException(habit.getId());
        }

        habit.synchronizePersistenceVersion(previousVersion + 1);
        return habit;
    }
}
