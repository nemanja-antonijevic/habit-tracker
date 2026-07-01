package com.nantonijevic.habits.service;

import com.nantonijevic.habits.domain.Habit;
import com.nantonijevic.habits.domain.HabitCompletion;
import com.nantonijevic.habits.domain.HabitCompletionStat;
import com.nantonijevic.habits.domain.HabitNotFoundException;
import com.nantonijevic.habits.domain.HabitVersionConflictException;
import com.nantonijevic.habits.dto.HabitStatsView;
import com.nantonijevic.habits.event.HabitCompletedEvent;
import com.nantonijevic.habits.event.HabitUncompletedEvent;
import com.nantonijevic.habits.exception.InvalidDateRangeException;
import com.nantonijevic.habits.repository.HabitCompletionRepository;
import com.nantonijevic.habits.repository.HabitCompletionStatRepository;
import com.nantonijevic.habits.repository.HabitRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;

@Service
public class HabitService {

    private static final Logger logger = LoggerFactory.getLogger(HabitService.class);

    private final HabitRepository habitRepository;
    private final HabitCompletionRepository completionRepository;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final HabitCompletionStatRepository completionStatRepository;

    public HabitService(HabitRepository habitRepository,
                        HabitCompletionRepository completionRepository,
                        ApplicationEventPublisher applicationEventPublisher,
                        HabitCompletionStatRepository completionStatRepository) {
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
    public Habit uncomplete(Long habitId, LocalDate today) {
        Habit habit = habitRepository.findById(habitId)
                .orElseThrow(() -> new HabitNotFoundException(habitId));

        completionRepository.deleteByHabitIdAndCompletedOn(habitId, today);

        LocalDate lastCompletion = completionRepository.findFirstByHabitIdOrderByCompletedOnDesc(habitId)
                .map(HabitCompletion::getCompletedOn).orElse(null);

        habit.decrementCompletionCount(today, lastCompletion);

        applicationEventPublisher.publishEvent(new HabitUncompletedEvent(habitId, today));

        logger.info("Habit uncompleted, habitId: {}, date: {}", habitId, today);

        return habitRepository.save(habit);
    }

    public Habit getById(Long habitId) {
        return habitRepository.findById(habitId)
                .orElseThrow(() -> new HabitNotFoundException(habitId));
    }

    @Transactional(readOnly = true)
    public HabitStatsView getStatsProjection(Long habitId, LocalDate today) {
        if (!habitRepository.existsById(habitId)) {
            throw new HabitNotFoundException(habitId);
        }
        Optional<HabitCompletionStat> lastRow =
                completionStatRepository.findFirstByHabitIdOrderByCompletedOnDesc(habitId);
        int currentStreak;
        if (lastRow.isEmpty()) {
            currentStreak = 0;
        } else {
            LocalDate lastCompleted = lastRow.get().getCompletedOn();
            boolean streakIsAlive = lastCompleted.equals(today) || lastCompleted.equals(today.minusDays(1));
            currentStreak = streakIsAlive ? lastRow.get().getCurrentStreak() : 0;
        }
        HabitStatsView aggregate = completionStatRepository.findStatsByHabitId(habitId);

        return new HabitStatsView(
                aggregate.completionCount(),
                aggregate.longestStreak(),
                aggregate.lastCompletedOn(),
                currentStreak);
    }

    // readOnly: list() may return N entities — skips dirty-check snapshots.
    // getById/getHistory intentionally omit it: single entity, benefit ≈ 0.
    @Transactional(readOnly = true)
    public Page<Habit> list(boolean includeArchived, String name, Pageable pageable) {
        Pageable effectivePageable = pageable.getSort().isUnsorted() ?
                PageRequest.of(
                        pageable.getPageNumber(),
                        pageable.getPageSize(),
                        Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))) :
                pageable;

        String normalizedName = name == null || name.isBlank()
                ? null
                : name.trim();

        return habitRepository.search(normalizedName, includeArchived, effectivePageable);
    }

    // readOnly: list()/getHistory() may return N entities — skips dirty-check snapshots.
    // getById intentionally omits it: single entity, benefit ≈ 0.
    @Transactional(readOnly = true)
    public Page<HabitCompletion> getHistory(
            Long habitId,
            LocalDate from,
            LocalDate to,
            Pageable pageable) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new InvalidDateRangeException();
        }
        habitRepository.findById(habitId)
                .orElseThrow(() -> new HabitNotFoundException(habitId));
        return completionRepository.findByHabitIdAndCompletedOnBetweenOptional(habitId, from, to, pageable);
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
