package com.nantonijevic.habits.service;

import com.nantonijevic.habits.domain.Habit;
import com.nantonijevic.habits.domain.HabitCompletion;
import com.nantonijevic.habits.domain.HabitCompletionStat;
import com.nantonijevic.habits.domain.HabitNotFoundException;
import com.nantonijevic.habits.domain.HabitVersionConflictException;
import com.nantonijevic.habits.dto.BulkCompleteResponse;
import com.nantonijevic.habits.dto.HabitCompletionRateResponse;
import com.nantonijevic.habits.dto.HabitDashboardResponse;
import com.nantonijevic.habits.dto.HabitStatsView;
import com.nantonijevic.habits.event.HabitCompletedEvent;
import com.nantonijevic.habits.event.HabitUncompletedEvent;
import com.nantonijevic.habits.exception.InvalidDateRangeException;
import com.nantonijevic.habits.repository.HabitCompletionRepository;
import com.nantonijevic.habits.repository.HabitCompletionStatRepository;
import com.nantonijevic.habits.repository.HabitMapper;
import com.nantonijevic.habits.repository.HabitSearchRepository;
import com.nantonijevic.habits.repository.HabitWriteRepository;
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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class HabitService {

    private static final Logger logger = LoggerFactory.getLogger(HabitService.class);

    private final HabitSearchRepository habitSearchRepository;
    private final HabitWriteRepository habitWriteRepository;
    private final HabitMapper habitMapper;
    private final HabitCompletionRepository completionRepository;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final HabitCompletionStatRepository completionStatRepository;

    public HabitService(HabitSearchRepository habitSearchRepository,
                        HabitWriteRepository habitWriteRepository,
                        HabitMapper habitMapper,
                        HabitCompletionRepository completionRepository,
                        ApplicationEventPublisher applicationEventPublisher,
                        HabitCompletionStatRepository completionStatRepository) {
        this.habitSearchRepository = habitSearchRepository;
        this.habitWriteRepository = habitWriteRepository;
        this.habitMapper = habitMapper;
        this.completionRepository = completionRepository;
        this.applicationEventPublisher = applicationEventPublisher;
        this.completionStatRepository = completionStatRepository;
    }

    @Transactional
    public Habit complete(Long habitId, LocalDate today) {
        Habit habit = Optional.ofNullable(habitMapper.findById(habitId))
            .orElseThrow(() -> new HabitNotFoundException(habitId));

        boolean reallyCompleted = completeExistingHabit(habit, habitId, today);

        if (reallyCompleted) {
            return habitWriteRepository.saveWithMyBatis(habit);
        }

        return habit;
    }

    private boolean completeExistingHabit(Habit habit, Long habitId, LocalDate today) {
        boolean reallyCompleted = habit.complete(today);

        if (reallyCompleted) {
            completionRepository.save(new HabitCompletion(habitId, today));

            logger.info("Habit completed, habitId: {}, date: {}, currentStreak: {}",
                    habitId, today, habit.getCurrentStreak());

            applicationEventPublisher.publishEvent(new HabitCompletedEvent(
                    habitId,
                    today,
                    habit.getCurrentStreak(),
                    habit.getCompletionCount()
            ));

            logger.info("HabitCompletedEvent published, habitId: {}, date: {}, currentStreak: {}",
                    habitId, today, habit.getCurrentStreak());
        } else {
            logger.debug("Habit completion skipped (already completed), habitId: {}, date: {}", habitId, today);
        }

        return reallyCompleted;
    }

    @Transactional
    public BulkCompleteResponse bulkComplete(List<Long> habitIds, LocalDate today) {
        List<Long> completed = new ArrayList<>();
        List<Long> skipped = new ArrayList<>();
        List<Long> failed = new ArrayList<>();
        List<Long> notFound = new ArrayList<>();

        // One findById per id (N queries). This is a deliberate trade-off, not an
        // oversight: best-effort semantics need a per-item notFound verdict, so each
        // id is looked up individually. Acceptable at personal scale (tens of habits,
        // capped at 100 by @Size on the request); a batch findAllById would not tell
        // us which specific ids were missing without extra bookkeeping.
        for (Long habitId : habitIds) {
            var maybeHabit = Optional.ofNullable(
                habitMapper.findById(habitId)
            );
            if (maybeHabit.isEmpty()) {
                notFound.add(habitId);
                continue;
            }

            Habit habit = maybeHabit.get();

            if (habit.isArchived()) {
                failed.add(habitId);
                continue;
            }

            if (!habit.isScheduledFor(today)) {
                failed.add(habitId);
                continue;
            }

            if (habit.wasCompletedOn(today)) {
                skipped.add(habitId);
                continue;
            }

            boolean reallyCompleted = completeExistingHabit(
                habit,
                habitId,
                today
            );

            if (reallyCompleted) {
                habitWriteRepository.saveWithMyBatis(habit);
            }

            completed.add(habitId);
        }

        return new BulkCompleteResponse(completed, skipped, failed, notFound);
    }

    @Transactional
    public Habit create(String name, Set<DayOfWeek> scheduledDays) {
        Habit habit = new Habit(name);

        EnumSet<DayOfWeek> effectiveScheduledDays = scheduledDays == null
                ? EnumSet.allOf(DayOfWeek.class)
                : EnumSet.copyOf(scheduledDays);

        habit.setScheduledDays(effectiveScheduledDays);
        Habit saved = habitWriteRepository.saveWithMyBatis(habit);
        logger.info("Habit created, habitId: {}", saved.getId());
        return saved;
    }

    @Transactional
    public Habit uncomplete(Long habitId, LocalDate today) {
        Habit habit = Optional.ofNullable(habitMapper.findById(habitId))
            .orElseThrow(() -> new HabitNotFoundException(habitId));

        completionRepository.deleteByHabitIdAndCompletedOn(habitId, today);

        List<LocalDate> remainingCompletionDates = completionRepository
            .findByHabitIdOrderByCompletedOnDesc(habitId)
            .stream()
            .map(HabitCompletion::getCompletedOn)
            .toList();

        habit.decrementCompletionCount(today, remainingCompletionDates);

        applicationEventPublisher.publishEvent(
            new HabitUncompletedEvent(habitId, today)
        );

        logger.info("Habit uncompleted, habitId: {}, date: {}", habitId, today);

        return habitWriteRepository.saveWithMyBatis(habit);
    }

    public Habit getById(Long habitId) {
        return Optional.ofNullable(habitMapper.findById(habitId))
                .orElseThrow(() -> new HabitNotFoundException(habitId));
    }

    @Transactional(readOnly = true)
    public HabitStatsView getStatsProjection(Long habitId, LocalDate today) {
        Habit habit = Optional.ofNullable(
            habitMapper.findById(habitId)
        ).orElseThrow(() -> new HabitNotFoundException(habitId));
        Optional<HabitCompletionStat> lastRow =
                completionStatRepository.findFirstByHabitIdOrderByCompletedOnDesc(habitId);
        int currentStreak;
        if (lastRow.isEmpty()) {
            currentStreak = 0;
        } else {
            LocalDate lastCompleted = lastRow.get().getCompletedOn();
            boolean streakIsAlive =
                habit.isStreakAliveGiven(lastCompleted, today);
            currentStreak = streakIsAlive ? lastRow.get().getCurrentStreak() : 0;
        }
        HabitStatsView aggregate = completionStatRepository.findStatsByHabitId(habitId);

        return new HabitStatsView(
                aggregate.completionCount(),
                aggregate.longestStreak(),
                aggregate.lastCompletedOn(),
                currentStreak);
    }

    @Transactional(readOnly = true)
    public HabitCompletionRateResponse getCompletionRate(
        Long habitId,
        LocalDate from,
        LocalDate to) {
        if (from.isAfter(to)) {
            throw new InvalidDateRangeException();
        }

        Habit habit = Optional.ofNullable(habitMapper.findById(habitId))
            .orElseThrow(() -> new HabitNotFoundException(habitId));

        LocalDate createdDate = LocalDate.ofInstant(
            habit.getCreatedAt(),
            ZoneId.systemDefault()
        );

        LocalDate effectiveFrom = from.isAfter(createdDate)
            ? from
            : createdDate;

        if (effectiveFrom.isAfter(to)) {
            return new HabitCompletionRateResponse(0, 0, null);
        }

        Set<DayOfWeek> scheduledDays = habit.getScheduledDays();

        long scheduled = countScheduledOccurrences(
            effectiveFrom,
            to,
            scheduledDays
        );

        List<LocalDate> completedDates =
            completionStatRepository.findCompletedDatesInPeriod(
                habitId,
                effectiveFrom,
                to
            );

        long completed = completedDates.stream()
            .filter(date ->
                scheduledDays.contains(date.getDayOfWeek())
            )
            .count();

        BigDecimal rate = scheduled == 0
            ? null
            : BigDecimal.valueOf(completed)
            .divide(
                BigDecimal.valueOf(scheduled),
                4,
                RoundingMode.HALF_UP
            );

        return new HabitCompletionRateResponse(
            scheduled,
            completed,
            rate
        );
    }

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

        return habitSearchRepository.search(normalizedName, includeArchived, effectivePageable);
    }

    @Transactional(readOnly = true)
    public Page<HabitCompletion> getHistory(
            Long habitId,
            LocalDate from,
            LocalDate to,
            Pageable pageable) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new InvalidDateRangeException();
        }
        if (!habitMapper.existsById(habitId)) {
            throw new HabitNotFoundException(habitId);
        }
        return completionRepository.findByHabitIdAndCompletedOnBetweenOptional(habitId, from, to, pageable);
    }

    @Transactional
    public Habit update(Long habitId, Long version, String name, Set<DayOfWeek> scheduledDays) {
        Habit habit = Optional.ofNullable(habitMapper.findById(habitId))
            .orElseThrow(() -> new HabitNotFoundException(habitId));

        if (!habit.getVersion().equals(version)) {
            throw new HabitVersionConflictException(habitId);
        }

        habit.setName(name);

        if (scheduledDays != null) {
            habit.setScheduledDays(EnumSet.copyOf(scheduledDays));
        }

        logger.info("Habit updated, habitId: {}, version: {}", habitId, version);
        return habitWriteRepository.saveWithMyBatis(habit);
    }

    @Transactional
    public Habit archive(Long habitId) {
        Habit habit = Optional.ofNullable(habitMapper.findById(habitId))
            .orElseThrow(() -> new HabitNotFoundException(habitId));

        habit.archive();

        logger.info("Habit archived, habitId: {}", habitId);

        return habitWriteRepository.saveWithMyBatis(habit);
    }

    @Transactional
    public Habit unarchive(Long habitId) {
        Habit habit = Optional.ofNullable(habitMapper.findById(habitId))
            .orElseThrow(() -> new HabitNotFoundException(habitId));

        habit.unarchive();

        logger.info("Habit unarchived, habitId: {}", habitId);

        return habitWriteRepository.saveWithMyBatis(habit);
    }

    @Transactional
    public void delete(Long habitId) {
        if (!habitMapper.existsById(habitId)) {
            throw new HabitNotFoundException(habitId);
        }

        habitMapper.deleteById(habitId);

        logger.info("Habit deleted, habitId: {}", habitId);
    }

    @Transactional(readOnly = true)
    public Page<Habit> dueToday(LocalDate today, Pageable pageable) {
        // scheduled_days je CSV-serijalizovana kolona, pa filtriranje po danu ne
        // radimo u SQL WHERE — učitavamo aktivne habite i filtriramo u memoriji.
        // Prihvatljivo za ličnu skalu (desetine habita); nije za velike skupove.
        List<Habit> filtered = habitMapper.findActive()
                .stream()
                .filter(habit -> isDueToday(habit, today))
                .toList();

        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), filtered.size());

        List<Habit> pageContent = start >= filtered.size()
                ? List.of()
                : filtered.subList(start, end);

        return new PageImpl<>(pageContent, pageable, filtered.size());
    }

    @Transactional(readOnly = true)
    public long countDueToday(LocalDate today) {
        return habitMapper.findActive()
                .stream()
                .filter(habit -> isDueToday(habit, today))
                .count();
    }

    private boolean isDueToday(Habit habit, LocalDate today) {
        return habit.isScheduledFor(today)
                && !habit.wasCompletedOn(today);
    }

    @Transactional(readOnly = true)
    public HabitDashboardResponse getDashboardStats(LocalDate today) {
        List<Habit> activeHabits = habitMapper.findActive();

        List<Long> activeHabitIds = activeHabits.stream()
            .map(Habit::getId)
            .toList();

        Map<Long, HabitCompletionStat> latestStatsByHabitId =
            activeHabitIds.isEmpty()
                ? Map.of()
                : completionStatRepository.findLatestByHabitIds(activeHabitIds)
                .stream()
                .collect(Collectors.toMap(
                    HabitCompletionStat::getHabitId,
                    Function.identity()
                ));

        long dueToday = 0;
        long completedToday = 0;
        long activeStreaks = 0;
        int longestActiveStreak = 0;

        for (Habit habit : activeHabits) {
            if (habit.isScheduledFor(today)) {
                dueToday++;

                if (habit.wasCompletedOn(today)) {
                    completedToday++;
                }
            }

            HabitCompletionStat latestStat =
                latestStatsByHabitId.get(habit.getId());

            int currentStreak = 0;

            if (latestStat != null) {
                LocalDate lastCompleted = latestStat.getCompletedOn();

                boolean streakIsAlive =
                    habit.isStreakAliveGiven(lastCompleted, today);

                if (streakIsAlive) {
                    currentStreak = latestStat.getCurrentStreak();
                }
            }

            if (currentStreak > 0) {
                activeStreaks++;
                longestActiveStreak = Math.max(
                    longestActiveStreak,
                    currentStreak
                );
            }
        }

        return new HabitDashboardResponse(
            dueToday,
            completedToday,
            activeStreaks,
            longestActiveStreak,
            activeHabits.size()
        );
    }

    private long countScheduledOccurrences(
        LocalDate from,
        LocalDate to,
        Set<DayOfWeek> scheduledDays) {
        long count = 0;
        LocalDate date = from;

        while (!date.isAfter(to)) {
            if (scheduledDays.contains(date.getDayOfWeek())) {
                count++;
            }

            if (date.equals(to)) {
                break;
            }

            date = date.plusDays(1);
        }

        return count;
    }
}
