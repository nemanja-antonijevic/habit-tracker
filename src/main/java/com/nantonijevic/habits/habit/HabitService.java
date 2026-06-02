package com.nantonijevic.habits.habit;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;

@Service
public class HabitService {

    private final HabitRepository habitRepository;
    private final HabitCompletionRepository completionRepository;

    public HabitService(HabitRepository habitRepository, HabitCompletionRepository completionRepository) {
        this.habitRepository = habitRepository;
        this.completionRepository = completionRepository;
    }

    @Transactional
    public Habit complete(Long habitId, LocalDate today) {
        Habit habit = habitRepository.findById(habitId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Habit not found"));

        boolean reallyCompleted = habit.complete(today);
        if (reallyCompleted) {
            completionRepository.save(new HabitCompletion(habitId, today));
        }
        return habitRepository.save(habit);
    }

}
