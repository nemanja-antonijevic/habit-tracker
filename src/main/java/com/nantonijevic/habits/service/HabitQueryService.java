package com.nantonijevic.habits.service;

import com.nantonijevic.habits.config.RedisCacheConfig;
import com.nantonijevic.habits.domain.Habit;
import com.nantonijevic.habits.domain.HabitCompletion;
import com.nantonijevic.habits.domain.HabitCompletionStat;
import com.nantonijevic.habits.domain.HabitNotFoundException;
import com.nantonijevic.habits.dto.HabitCompletionRateResponse;
import com.nantonijevic.habits.dto.HabitDashboardResponse;
import com.nantonijevic.habits.dto.HabitStatsView;
import com.nantonijevic.habits.exception.InvalidDateRangeException;
import com.nantonijevic.habits.repository.HabitCompletionRepository;
import com.nantonijevic.habits.repository.HabitCompletionStatRepository;
import com.nantonijevic.habits.repository.HabitMapper;
import com.nantonijevic.habits.repository.HabitSearchRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class HabitQueryService {

    private final HabitSearchRepository habitSearchRepository;
    private final HabitMapper habitMapper;
    private final HabitCompletionRepository completionRepository;
    private final HabitCompletionStatRepository completionStatRepository;
    private final Clock clock;

    public HabitQueryService(
        HabitSearchRepository habitSearchRepository,
        HabitMapper habitMapper,
        HabitCompletionRepository completionRepository,
        HabitCompletionStatRepository completionStatRepository,
        Clock clock
    ) {
        this.habitSearchRepository = habitSearchRepository;
        this.habitMapper = habitMapper;
        this.completionRepository = completionRepository;
        this.completionStatRepository = completionStatRepository;
        this.clock = clock;
    }

    public Habit getById(Long ownerId, Long habitId) {
        return Optional.ofNullable(
            habitMapper.findById(ownerId, habitId)
        ).orElseThrow(
            () -> new HabitNotFoundException(habitId)
        );
    }

    @Transactional(readOnly = true)
    public Page<Habit> list(
        Long ownerId,
        boolean includeArchived,
        String name,
        Pageable pageable
    ) {
        Pageable effectivePageable =
            pageable.getSort().isUnsorted()
                ? PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                Sort.by(
                    Sort.Order.desc("createdAt"),
                    Sort.Order.desc("id")
                )
            )
                : pageable;

        String normalizedName =
            name == null || name.isBlank()
                ? null
                : name.trim();

        return habitSearchRepository.search(
            ownerId,
            normalizedName,
            includeArchived,
            effectivePageable
        );
    }

    @Transactional(readOnly = true)
    public Page<HabitCompletion> getHistory(
        Long ownerId,
        Long habitId,
        LocalDate from,
        LocalDate to,
        Pageable pageable
    ) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new InvalidDateRangeException();
        }

        if (!habitMapper.existsById(ownerId, habitId)) {
            throw new HabitNotFoundException(habitId);
        }

        return completionRepository
            .findByHabitIdAndCompletedOnBetweenOptional(
                habitId,
                from,
                to,
                pageable
            );
    }

    @Transactional(readOnly = true)
    public Page<Habit> dueToday(
        Long ownerId,
        LocalDate today,
        Pageable pageable
    ) {
        // scheduled_days je CSV-serijalizovana kolona, pa filtriranje po danu ne
        // radimo u SQL WHERE — učitavamo aktivne habite i filtriramo u memoriji.
        // Prihvatljivo za ličnu skalu (desetine habita); nije za velike skupove.
        List<Habit> filtered = habitMapper.findActive(ownerId)
            .stream()
            .filter(habit -> isDueToday(habit, today))
            .toList();

        int start = (int) pageable.getOffset();
        int end = Math.min(
            start + pageable.getPageSize(),
            filtered.size()
        );

        List<Habit> pageContent =
            start >= filtered.size()
                ? List.of()
                : filtered.subList(start, end);

        return new PageImpl<>(
            pageContent,
            pageable,
            filtered.size()
        );
    }

    @Transactional(readOnly = true)
    public long countDueToday(Long ownerId, LocalDate today) {
        return habitMapper.findActive(ownerId)
            .stream()
            .filter(habit -> isDueToday(habit, today))
            .count();
    }

    @Transactional(readOnly = true)
    public HabitCompletionRateResponse getCompletionRate(
        Long ownerId,
        Long habitId,
        LocalDate from,
        LocalDate to
    ) {
        if (from.isAfter(to)) {
            throw new InvalidDateRangeException();
        }

        Habit habit = Optional.ofNullable(
            habitMapper.findById(ownerId, habitId)
        ).orElseThrow(
            () -> new HabitNotFoundException(habitId)
        );

        LocalDate createdDate = LocalDate.ofInstant(
            habit.getCreatedAt(),
            clock.getZone()
        );

        LocalDate effectiveFrom =
            from.isAfter(createdDate)
                ? from
                : createdDate;

        if (effectiveFrom.isAfter(to)) {
            return new HabitCompletionRateResponse(
                0,
                0,
                null
            );
        }

        Set<DayOfWeek> scheduledDays =
            habit.getScheduledDays();

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

        BigDecimal rate =
            scheduled == 0
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
    public HabitStatsView getStatsProjection(
        Long ownerId,
        Long habitId,
        LocalDate today
    ) {
        Habit habit = Optional.ofNullable(
            habitMapper.findById(ownerId, habitId)
        ).orElseThrow(
            () -> new HabitNotFoundException(habitId)
        );

        HabitCompletionStat latestStat =
            completionStatRepository
                .findFirstByHabitIdOrderByCompletedOnDesc(
                    habitId
                )
                .orElse(null);

        int currentStreak = currentStreak(
            habit,
            latestStat,
            today
        );

        HabitStatsView aggregate =
            completionStatRepository.findStatsByHabitId(
                habitId
            );

        return new HabitStatsView(
            aggregate.completionCount(),
            aggregate.longestStreak(),
            aggregate.lastCompletedOn(),
            currentStreak
        );
    }

    @Transactional(readOnly = true)
    @Cacheable(
        cacheNames =
            RedisCacheConfig.DASHBOARD_STATS_CACHE,
        keyGenerator =
            "dashboardCacheKeyGenerator",
        condition =
            "@environment.getProperty("
                + "'spring.cache.type', 'redis'"
                + ") == 'redis'"
    )
    public HabitDashboardResponse getDashboardStats(
        Long ownerId,
        LocalDate today
    ) {
        List<Habit> activeHabits =
            habitMapper.findActive(ownerId);

        List<Long> activeHabitIds =
            activeHabits.stream()
                .map(Habit::getId)
                .toList();

        Map<Long, HabitCompletionStat>
            latestStatsByHabitId =
            activeHabitIds.isEmpty()
                ? Map.of()
                : completionStatRepository
                .findLatestByHabitIds(
                    activeHabitIds
                )
                .stream()
                .collect(
                    Collectors.toMap(
                        HabitCompletionStat::getHabitId,
                        Function.identity()
                    )
                );

        long dueToday = 0;
        long completedToday = 0;
        long activeStreaks = 0;
        int longestActiveStreak = 0;

        for (Habit habit : activeHabits) {
            if (habit.isScheduledFor(today)) {
                dueToday++;

                if (habit.wasCompletedOn(
                    today,
                    clock.getZone()
                )) {
                    completedToday++;
                }
            }

            int currentStreak = currentStreak(
                habit,
                latestStatsByHabitId.get(
                    habit.getId()
                ),
                today
            );

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

    private boolean isDueToday(
        Habit habit,
        LocalDate today
    ) {
        return habit.isScheduledFor(today)
            && !habit.wasCompletedOn(
            today,
            clock.getZone()
        );
    }

    private long countScheduledOccurrences(
        LocalDate from,
        LocalDate to,
        Set<DayOfWeek> scheduledDays
    ) {
        long count = 0;
        LocalDate date = from;

        while (!date.isAfter(to)) {
            if (scheduledDays.contains(
                date.getDayOfWeek()
            )) {
                count++;
            }

            if (date.equals(to)) {
                break;
            }

            date = date.plusDays(1);
        }

        return count;
    }

    private int currentStreak(
        Habit habit,
        HabitCompletionStat latestStat,
        LocalDate today
    ) {
        if (latestStat == null) {
            return 0;
        }

        boolean streakIsAlive =
            habit.isStreakAliveGiven(
                latestStat.getCompletedOn(),
                today
            );

        return streakIsAlive
            ? latestStat.getCurrentStreak()
            : 0;
    }
}
