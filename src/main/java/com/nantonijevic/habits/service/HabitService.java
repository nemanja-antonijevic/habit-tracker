package com.nantonijevic.habits.service;

import com.nantonijevic.habits.domain.Habit;
import com.nantonijevic.habits.domain.HabitCompletion;
import com.nantonijevic.habits.domain.HabitNotFoundException;
import com.nantonijevic.habits.domain.HabitVersionConflictException;
import com.nantonijevic.habits.dto.HabitStatsView;
import com.nantonijevic.habits.event.HabitCompletedEvent;
import com.nantonijevic.habits.repository.HabitCompletionRepository;
import com.nantonijevic.habits.repository.HabitCompletionStatRepository;
import com.nantonijevic.habits.repository.HabitRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class HabitService {

    private static final Logger logger = LoggerFactory.getLogger(HabitService.class);

    private final HabitRepository habitRepository;
    private final HabitCompletionRepository completionRepository;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final HabitCompletionStatRepository completionStatRepository;

    public HabitService(HabitRepository habitRepository, HabitCompletionRepository completionRepository, ApplicationEventPublisher applicationEventPublisher,  HabitCompletionStatRepository completionStatRepository) {
        this.habitRepository = habitRepository;
        this.completionRepository = completionRepository;
        this.applicationEventPublisher = applicationEventPublisher;
        this.completionStatRepository = completionStatRepository;
    }

    @Transactional
    public Habit complete(Long habitId, LocalDate today) {
        Habit habit = habitRepository.findById(habitId)
                .orElseThrow(() -> new HabitNotFoundException(habitId));

        boolean reallyCompleted = habit.complete(today);
        if (reallyCompleted) {
            completionRepository.save(new HabitCompletion(habitId, today));
            logger.info("Habit completed, habitId: {}, date: {}, currentStreak: {}",
                    habitId, today, habit.getCurrentStreak());
            applicationEventPublisher.publishEvent(new HabitCompletedEvent(
                    habitId, today, habit.getCurrentStreak(), habit.getCompletionCount()));
            logger.info("HabitCompletedEvent published, habitId: {}, date: {}, currentStreak: {}",
                    habitId, today, habit.getCurrentStreak());
        } else {
            logger.debug("Habit completion skipped (already completed), habitId: {}, date: {}", habitId, today);
        }
        return habitRepository.save(habit);
    }

    @Transactional
    public Habit create(String name) {
        Habit saved = habitRepository.save(new Habit(name));
        logger.info("Habit created, habitId: {}", saved.getId());
        return saved;
    }

    @Transactional
    public Habit uncomplete(Long habitId) {
        Habit habit = habitRepository.findById(habitId)
                .orElseThrow(() -> new HabitNotFoundException(habitId));
        habit.decrementCompletionCount();
        logger.info("Habit uncompleted, habitId: {}", habitId);
        return habitRepository.save(habit);
    }

    public Habit getById(Long habitId) {
        return habitRepository.findById(habitId)
                .orElseThrow(() -> new HabitNotFoundException(habitId));
    }

    @Transactional(readOnly = true)
    public HabitStatsView getStatsProjection(Long habitId) {
        if (!habitRepository.existsById(habitId)) {
            throw new HabitNotFoundException(habitId);
        }
        return completionStatRepository.findStatsByHabitId(habitId);
    }

    // readOnly: list() may return N entities — skips dirty-check snapshots.
    // getById/getHistory intentionally omit it: single entity, benefit ≈ 0.
    @Transactional(readOnly = true)
    public Page<Habit> list(Pageable pageable) {
        return habitRepository.findByArchivedFalse(pageable);
    }

    public List<HabitCompletion> getHistory(Long habitId) {
        habitRepository.findById(habitId)
                .orElseThrow(() -> new HabitNotFoundException(habitId));
        return completionRepository.findByHabitIdOrderByCompletedOnDesc(habitId);
    }

    @Transactional
    public Habit update(Long habitId, Long version, String name){
        Habit habit = habitRepository.findById(habitId)
                .orElseThrow(() -> new HabitNotFoundException(habitId));

        if (!habit.getVersion().equals(version)) {
            throw new HabitVersionConflictException(habitId);
        }
        habit.setName(name);
        logger.info("Habit updated, habitId: {}, version: {}", habitId, version);
        return habitRepository.save(habit);
    }

    @Transactional
    public Habit archive(Long habitId) {
        Habit habit = habitRepository.findById(habitId)
                .orElseThrow(() -> new HabitNotFoundException(habitId));
        habit.archive();
        logger.info("Habit archived, habitId: {}",
                habitId);
        return habitRepository.save(habit);
    }

    @Transactional
    public Habit unarchive(Long habitId) {
        Habit habit = habitRepository.findById(habitId)
                .orElseThrow(() -> new HabitNotFoundException(habitId));
        habit.unarchive();
        logger.info("Habit unarchived, habitId: {}",
                habitId);
        return habitRepository.save(habit);
    }

    @Transactional
    public void delete(Long habitId) {
        habitRepository.findById(habitId)
                .orElseThrow(() -> new HabitNotFoundException(habitId));
        habitRepository.deleteById(habitId);
        logger.info("Habit deleted, habitId: {}",
                habitId);
    }
}
