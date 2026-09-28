package com.nantonijevic.habits.service;

import com.nantonijevic.habits.domain.Habit;
import com.nantonijevic.habits.domain.HabitNotFoundException;
import com.nantonijevic.habits.domain.HabitSkip;
import com.nantonijevic.habits.domain.HabitSkipAlreadyUsedException;
import com.nantonijevic.habits.event.DashboardChangedEvent;
import com.nantonijevic.habits.repository.HabitMapper;
import com.nantonijevic.habits.repository.HabitSkipRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Optional;

@Service
public class HabitSkipService {

    private static final String
        MONTHLY_UNIQUE_CONSTRAINT =
        "uq_habit_skips_habit_month";

    private final HabitMapper habitMapper;

    private final HabitSkipRepository skipRepository;

    private final ApplicationEventPublisher
        eventPublisher;

    private final Clock clock;

    public HabitSkipService(
        HabitMapper habitMapper,
        HabitSkipRepository skipRepository,
        ApplicationEventPublisher eventPublisher,
        Clock clock
    ) {
        this.habitMapper = habitMapper;
        this.skipRepository = skipRepository;
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
}
