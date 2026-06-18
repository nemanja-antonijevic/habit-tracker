package com.nantonijevic.habits.event;

import com.nantonijevic.habits.domain.HabitCompletionStat;
import com.nantonijevic.habits.repository.HabitCompletionStatRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class HabitCompletedEventConsumer {

    private static final Logger logger = LoggerFactory.getLogger(HabitCompletedEventConsumer.class);

    private final HabitCompletionStatRepository repository;

    public HabitCompletedEventConsumer(HabitCompletionStatRepository repository) {
        this.repository = repository;
    }

    @KafkaListener(topics = "habit-completed", groupId = "habit-stats")
    public void on(HabitCompletedEvent event) {
        HabitCompletionStat habitCompletionStat =
                new HabitCompletionStat(event.habitId(), event.completedOn(), event.currentStreak(), event.completionCount());

        try {
            repository.save(habitCompletionStat);
        } catch (DataIntegrityViolationException e) {
            logger.debug("Skipped duplicate completion event for habit {} on {}",
                    event.habitId(), event.completedOn());
        }
    }
}
