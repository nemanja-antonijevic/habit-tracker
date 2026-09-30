package com.nantonijevic.habits.service;

import com.nantonijevic.habits.domain.Habit;
import com.nantonijevic.habits.domain.HabitCompletion;
import com.nantonijevic.habits.domain.HabitNotFoundException;
import com.nantonijevic.habits.domain.HabitSkip;
import com.nantonijevic.habits.domain.HabitSkipAlreadyUsedException;
import com.nantonijevic.habits.domain.HabitSkipInUseException;
import com.nantonijevic.habits.domain.HabitSkipNotFoundException;
import com.nantonijevic.habits.event.DashboardChangedEvent;
import com.nantonijevic.habits.repository.HabitCompletionRepository;
import com.nantonijevic.habits.repository.HabitMapper;
import com.nantonijevic.habits.repository.HabitSkipRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@Service
public class HabitSkipService {

    private static final String
        MONTHLY_UNIQUE_CONSTRAINT =
        "uq_habit_skips_habit_month";

    private final HabitMapper habitMapper;

    private final HabitSkipRepository skipRepository;

    private final HabitCompletionRepository
        completionRepository;

    private final ApplicationEventPublisher
        eventPublisher;

    private final Clock clock;

    public HabitSkipService(
        HabitMapper habitMapper,
        HabitSkipRepository skipRepository,
        HabitCompletionRepository completionRepository,
        ApplicationEventPublisher eventPublisher,
        Clock clock
    ) {
        this.habitMapper = habitMapper;
        this.skipRepository = skipRepository;
        this.completionRepository = completionRepository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional
    public HabitSkip skip(
        Long ownerId,
        Long habitId,
        LocalDate skippedOn
    ) {
        Habit habit = Optional.ofNullable(
                habitMapper.findById(
                    ownerId,
                    habitId
                )
            )
            .orElseThrow(
                () -> new HabitNotFoundException(
                    habitId
                )
            );

        habit.validateSkipOn(
            skippedOn,
            clock.getZone()
        );

        LocalDate monthStart =
            skippedOn.withDayOfMonth(1);

        if (skipRepository
            .findByHabitIdAndMonthStart(
                habitId,
                monthStart
            )
            .isPresent()) {
            throw new HabitSkipAlreadyUsedException(
                habitId,
                skippedOn
            );
        }

        HabitSkip skip =
            new HabitSkip(
                habitId,
                skippedOn
            );

        try {
            HabitSkip saved =
                skipRepository.saveAndFlush(skip);

            eventPublisher.publishEvent(
                new DashboardChangedEvent()
            );

            return saved;
        } catch (
            DataIntegrityViolationException exception
        ) {
            if (isMonthlyConstraintViolation(
                exception
            )) {
                throw new HabitSkipAlreadyUsedException(
                    habitId,
                    skippedOn
                );
            }

            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public HabitSkip getCurrentMonthSkip(
        Long ownerId,
        Long habitId,
        LocalDate today
    ) {
        Optional.ofNullable(
                habitMapper.findById(
                    ownerId,
                    habitId
                )
            )
            .orElseThrow(
                () -> new HabitNotFoundException(
                    habitId
                )
            );

        LocalDate monthStart =
            today.withDayOfMonth(1);

        return skipRepository
            .findByHabitIdAndMonthStart(
                habitId,
                monthStart
            )
            .orElseThrow(
                () -> new HabitSkipNotFoundException(
                    habitId,
                    today
                )
            );
    }

    @Transactional
    public void removeCurrentMonthSkip(
        Long ownerId,
        Long habitId,
        LocalDate today
    ) {
        Habit habit = Optional.ofNullable(
                habitMapper.findById(
                    ownerId,
                    habitId
                )
            )
            .orElseThrow(
                () -> new HabitNotFoundException(
                    habitId
                )
            );

        LocalDate monthStart =
            today.withDayOfMonth(1);

        HabitSkip skip = skipRepository
            .findByHabitIdAndMonthStart(
                habitId,
                monthStart
            )
            .orElseThrow(
                () ->
                    new HabitSkipNotFoundException(
                        habitId,
                        today
                    )
            );

        if (currentStreakDependsOnSkip(
            habit,
            skip,
            today
        )) {
            throw new HabitSkipInUseException(
                habitId,
                skip.skippedOn()
            );
        }

        skipRepository.delete(skip);

        eventPublisher.publishEvent(
            new DashboardChangedEvent()
        );
    }

    private boolean isMonthlyConstraintViolation(
        Throwable throwable
    ) {
        Throwable current = throwable;

        while (current != null) {
            String message = current.getMessage();

            if (message != null
                && message
                .toLowerCase(Locale.ROOT)
                .contains(
                    MONTHLY_UNIQUE_CONSTRAINT
                )) {
                return true;
            }

            current = current.getCause();
        }

        return false;
    }

    private boolean currentStreakDependsOnSkip(
        Habit habit,
        HabitSkip skip,
        LocalDate today
    ) {
        List<LocalDate> completionDates =
            completionRepository
                .findByHabitIdOrderByCompletedOnDesc(
                    skip.habitId()
                )
                .stream()
                .map(
                    HabitCompletion::getCompletedOn
                )
                .sorted()
                .toList();

        if (completionDates.isEmpty()) {
            return false;
        }

        Set<LocalDate> skippedDates =
            new HashSet<>(
                skipRepository
                    .findByHabitIdAndSkippedOnBetween(
                        skip.habitId(),
                        completionDates.getFirst(),
                        today
                    )
                    .stream()
                    .map(HabitSkip::skippedOn)
                    .toList()
            );

        Habit.StreakSnapshot withSkip =
            habit.calculateStreaks(
                completionDates,
                today,
                skippedDates
            );

        skippedDates.remove(
            skip.skippedOn()
        );

        Habit.StreakSnapshot withoutSkip =
            habit.calculateStreaks(
                completionDates,
                today,
                skippedDates
            );

        return !withSkip.equals(withoutSkip);
    }
}
