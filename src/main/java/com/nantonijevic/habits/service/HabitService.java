package com.nantonijevic.habits.service;

import com.nantonijevic.habits.config.RedisCacheConfig;
import com.nantonijevic.habits.domain.Habit;
import com.nantonijevic.habits.domain.HabitCompletion;
import com.nantonijevic.habits.domain.HabitCompletionStat;
import com.nantonijevic.habits.domain.HabitNotFoundException;
import com.nantonijevic.habits.domain.HabitVersionConflictException;
import com.nantonijevic.habits.dto.BulkCompleteResponse;
import com.nantonijevic.habits.dto.HabitCompletionRateResponse;
import com.nantonijevic.habits.dto.HabitDashboardResponse;
import com.nantonijevic.habits.dto.HabitStatsView;
import com.nantonijevic.habits.event.DashboardChangedEvent;
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
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public HabitService(HabitSearchRepository habitSearchRepository,
                        HabitWriteRepository habitWriteRepository,
                        HabitMapper habitMapper,
                        HabitCompletionRepository completionRepository,
                        ApplicationEventPublisher applicationEventPublisher,
                        HabitCompletionStatRepository completionStatRepository,
                        TransactionTemplate transactionTemplate,
                        Clock clock) {
        this.habitSearchRepository = habitSearchRepository;
        this.habitWriteRepository = habitWriteRepository;
        this.habitMapper = habitMapper;
        this.completionRepository = completionRepository;
        this.applicationEventPublisher = applicationEventPublisher;
        this.completionStatRepository = completionStatRepository;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
    }

    public Habit complete(Long habitId, LocalDate today) {
        try {
            return executeCompleteAttempt(habitId, today);
        } catch (HabitVersionConflictException firstConflict) {
            logger.info(
                "Habit completion version conflict; retrying once, "
                    + "habitId: {}, date: {}, reason: {}",
                habitId,
                today,
                firstConflict.getMessage()
            );

            try {
                return executeCompleteAttempt(habitId, today);
            } catch (HabitVersionConflictException retryConflict) {
                logger.warn(
                    "Habit completion retry exhausted, "
                        + "habitId: {}, date: {}, reason: {}",
                    habitId,
                    today,
                    retryConflict.getMessage()
                );

                throw retryConflict;
            }
        }
    }

    private Habit executeCompleteAttempt(
        Long habitId,
        LocalDate today
    ) {
        return Objects.requireNonNull(
            transactionTemplate.execute(
                status -> completeAttempt(habitId, today)
            ),
            "Completion transaction must return a Habit"
        );
    }

    private Habit completeAttempt(Long habitId, LocalDate today) {
        Habit habit = Optional.ofNullable(
                habitMapper.findById(habitId)
            )
            .orElseThrow(
                () -> new HabitNotFoundException(habitId)
            );

        boolean reallyCompleted =
            completeExistingHabit(habit, habitId, today);

        if (reallyCompleted) {
            applicationEventPublisher.publishEvent(
                new DashboardChangedEvent()
            );
        }

        return habit;
    }

    private boolean completeExistingHabit(Habit habit, Long habitId, LocalDate today) {
        boolean reallyCompleted = habit.complete(
            today,
            clock.getZone()
        );

        if (reallyCompleted) {
            habitWriteRepository.save(habit);
            completionRepository.save(new HabitCompletion(habitId, today));

            logger.info("Habit completed, habitId: {}, date: {}, currentStreak: {}",
                    habitId, today, habit.getCurrentStreak());

            applicationEventPublisher.publishEvent(new HabitCompletedEvent(
                    habitId,
                    today,
                    habit.getCurrentStreak(),
                    habit.getCompletionCount()
            ));
        } else {
            logger.debug("Habit completion skipped (already completed), habitId: {}, date: {}", habitId, today);
        }

        return reallyCompleted;
    }

    // Intentionally not @Transactional: every item attempt owns its transaction.
    // Wrapping this method in a transaction would make TransactionTemplate join it
    // through REQUIRED propagation and break per-item durability.
    public BulkCompleteResponse bulkComplete(
        List<Long> habitIds,
        LocalDate today
    ) {
        List<Long> completed = new ArrayList<>();
        List<Long> skipped = new ArrayList<>();
        List<Long> failed = new ArrayList<>();
        List<Long> notFound = new ArrayList<>();
        List<Long> conflicted = new ArrayList<>();

        // One transactional findById attempt per id. This is deliberate:
        // best-effort semantics require an independent verdict and commit boundary
        // for every item. The request is capped at 100 ids.
        for (Long habitId : habitIds) {
            BulkCompleteOutcome outcome =
                completeBulkItemWithRetry(habitId, today);

            switch (outcome) {
                case COMPLETED -> completed.add(habitId);
                case SKIPPED -> skipped.add(habitId);
                case FAILED -> failed.add(habitId);
                case NOT_FOUND -> notFound.add(habitId);
                case CONFLICTED -> conflicted.add(habitId);
            }
        }

        return new BulkCompleteResponse(
            completed,
            skipped,
            failed,
            notFound,
            conflicted
        );
    }

    private BulkCompleteOutcome completeBulkItemWithRetry(
        Long habitId,
        LocalDate today
    ) {
        try {
            return executeBulkCompleteAttempt(habitId, today);
        } catch (HabitVersionConflictException firstConflict) {
            logger.info(
                "Bulk habit completion version conflict; retrying once, "
                    + "habitId: {}, date: {}, reason: {}",
                habitId,
                today,
                firstConflict.getMessage()
            );

            try {
                return executeBulkCompleteAttempt(habitId, today);
            } catch (HabitVersionConflictException retryConflict) {
                logger.warn(
                    "Bulk habit completion retry exhausted, "
                        + "habitId: {}, date: {}, reason: {}",
                    habitId,
                    today,
                    retryConflict.getMessage()
                );

                return BulkCompleteOutcome.CONFLICTED;
            }
        }
    }

    private BulkCompleteOutcome executeBulkCompleteAttempt(
        Long habitId,
        LocalDate today
    ) {
        return Objects.requireNonNull(
            transactionTemplate.execute(
                status -> bulkCompleteAttempt(habitId, today)
            ),
            "Bulk completion transaction must return an outcome"
        );
    }

    private BulkCompleteOutcome bulkCompleteAttempt(
        Long habitId,
        LocalDate today
    ) {
        Habit habit = habitMapper.findById(habitId);

        if (habit == null) {
            return BulkCompleteOutcome.NOT_FOUND;
        }

        if (habit.isArchived()) {
            return BulkCompleteOutcome.FAILED;
        }

        if (!habit.isScheduledFor(today)) {
            return BulkCompleteOutcome.FAILED;
        }

        if (habit.wasCompletedOn(today, clock.getZone())) {
            return BulkCompleteOutcome.SKIPPED;
        }

        boolean reallyCompleted =
            completeExistingHabit(habit, habitId, today);

        if (!reallyCompleted) {
            return BulkCompleteOutcome.SKIPPED;
        }

        applicationEventPublisher.publishEvent(
            new DashboardChangedEvent()
        );

        return BulkCompleteOutcome.COMPLETED;
    }

    @Transactional
    public Habit create(String name, Set<DayOfWeek> scheduledDays) {
        Habit habit = new Habit(name, clock.instant());

        EnumSet<DayOfWeek> effectiveScheduledDays = scheduledDays == null
                ? EnumSet.allOf(DayOfWeek.class)
                : EnumSet.copyOf(scheduledDays);

        habit.setScheduledDays(effectiveScheduledDays);
        Habit saved = habitWriteRepository.save(habit);
        applicationEventPublisher.publishEvent(
            new DashboardChangedEvent()
        );
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

        habit.decrementCompletionCount(
            today,
            remainingCompletionDates,
            clock.getZone()
        );

        applicationEventPublisher.publishEvent(
            new HabitUncompletedEvent(habitId, today)
        );

        logger.info("Habit uncompleted, habitId: {}, date: {}", habitId, today);

        Habit saved =
            habitWriteRepository.save(habit);

        applicationEventPublisher.publishEvent(
            new DashboardChangedEvent()
        );

        return saved;
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
            clock.getZone()
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
        Habit saved = habitWriteRepository.save(habit);

        applicationEventPublisher.publishEvent(
            new DashboardChangedEvent()
        );

        return saved;
    }

    @Transactional
    public Habit archive(Long habitId) {
        Habit habit = Optional.ofNullable(habitMapper.findById(habitId))
            .orElseThrow(() -> new HabitNotFoundException(habitId));

        habit.archive();

        logger.info("Habit archived, habitId: {}", habitId);

        Habit saved = habitWriteRepository.save(habit);

        applicationEventPublisher.publishEvent(
            new DashboardChangedEvent()
        );

        return saved;
    }

    @Transactional
    public Habit unarchive(Long habitId) {
        Habit habit = Optional.ofNullable(habitMapper.findById(habitId))
            .orElseThrow(() -> new HabitNotFoundException(habitId));

        habit.unarchive();

        logger.info("Habit unarchived, habitId: {}", habitId);

        Habit saved = habitWriteRepository.save(habit);

        applicationEventPublisher.publishEvent(
            new DashboardChangedEvent()
        );

        return saved;
    }

    @Transactional
    public void delete(Long habitId) {
        if (!habitMapper.existsById(habitId)) {
            throw new HabitNotFoundException(habitId);
        }

        habitMapper.deleteById(habitId);

        applicationEventPublisher.publishEvent(
            new DashboardChangedEvent()
        );

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
                && !habit.wasCompletedOn(today, clock.getZone());
    }

    @Transactional(readOnly = true)
    @Cacheable(
        cacheNames = RedisCacheConfig.DASHBOARD_STATS_CACHE,
        keyGenerator = "dashboardCacheKeyGenerator",
        condition = "@environment.getProperty("
            + "'spring.cache.type', 'redis'"
            + ") == 'redis'"
    )
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

                if (habit.wasCompletedOn(today, clock.getZone())) {
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

    private enum BulkCompleteOutcome {
        COMPLETED,
        SKIPPED,
        FAILED,
        NOT_FOUND,
        CONFLICTED
    }
}
