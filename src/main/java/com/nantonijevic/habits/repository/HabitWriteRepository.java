package com.nantonijevic.habits.repository;

import com.nantonijevic.habits.domain.Habit;

public interface HabitWriteRepository {

    Habit save(Habit habit);
}
