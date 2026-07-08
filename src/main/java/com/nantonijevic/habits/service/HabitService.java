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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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
    public Habit create(String name, Set<DayOfWeek> scheduledDays) {
        Habit habit = new Habit(name);

        EnumSet<DayOfWeek> effectiveScheduledDays = scheduledDays == null
                ? EnumSet.allOf(DayOfWeek.class)
                : EnumSet.copyOf(scheduledDays);

        habit.setScheduledDays(effectiveScheduledDays);
        Habit saved = habitRepository.save(habit);
        logger.info("Habit created, habitId: {}", saved.getId());
        return saved;
    }

    @Transactional
    public Habit uncomplete(Long habitId, LocalDate today) {
        Habit habit = habitRepository.findById(habitId)
                .orElseThrow(() -> new HabitNotFoundException(habitId));

        completionRepository.deleteByHabitIdAndCompletedOn(habitId, today);

        List<LocalDate> remainingCompletionDates = completionRepository
                .findByHabitIdOrderByCompletedOnDesc(habitId)
                .stream()
                .map(HabitCompletion::getCompletedOn)
                .toList();

        habit.decrementCompletionCount(today, remainingCompletionDates);

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
        Habit habit = habitRepository.findById(habitId)
                .orElseThrow(() -> new HabitNotFoundException(habitId));
        Optional<HabitCompletionStat> lastRow =
                completionStatRepository.findFirstByHabitIdOrderByCompletedOnDesc(habitId);
        int currentStreak;
        if (lastRow.isEmpty()) {
            currentStreak = 0;
        } else {
            LocalDate lastCompleted = lastRow.get().getCompletedOn();
            boolean streakIsAlive =
                    lastCompleted.equals(today)
                            || lastCompleted.equals(habit.previousScheduledDateBefore(today));
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
    public Habit update(Long habitId, Long version, String name, Set<DayOfWeek> scheduledDays) {
        Habit habit = habitRepository.findById(habitId)
                .orElseThrow(() -> new HabitNotFoundException(habitId));

        if (!habit.getVersion().equals(version)) {
            throw new HabitVersionConflictException(habitId);
        }

        habit.setName(name);

        if (scheduledDays != null) {
            habit.setScheduledDays(EnumSet.copyOf(scheduledDays));
        }

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

    @Transactional(readOnly = true)
    public Page<Habit> dueToday(LocalDate today, Pageable pageable) {
        // scheduledDays je @Convert-ovana kolona (serijalizovan string), pa se ne može
        // filtrirati u SQL WHERE — učitavamo aktivne habite i filtriramo u memoriji.
        // Prihvatljivo za ličnu skalu (desetine habita); nije za velike skupove.
        List<Habit> filtered = habitRepository.search(null, false, Pageable.unpaged())
                .getContent()
                .stream()
                .filter(habit -> habit.isScheduledFor(today))
                .filter(habit -> !habit.wasCompletedOn(today))
                .toList();

        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), filtered.size());

        List<Habit> pageContent = start >= filtered.size()
                ? List.of()
                : filtered.subList(start, end);

        return new PageImpl<>(pageContent, pageable, filtered.size());
    }
}
