package com.nantonijevic.habits.event;

import com.nantonijevic.habits.domain.HabitCompletionStat;
import com.nantonijevic.habits.repository.HabitCompletionStatRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class HabitCompletedEventConsumer {

    private static final Logger logger = LoggerFactory.getLogger(HabitCompletedEventConsumer.class);

    private final HabitCompletionStatRepository repository;


    public HabitCompletedEventConsumer(HabitCompletionStatRepository repository) {
        this.repository = repository;
    }

    @Transactional
    @KafkaListener(topics = "habit-completed", groupId = "habit-stats")
    public void on(HabitEvent event) {

        switch (event) {
            case HabitCompletedEvent e   -> {
                HabitCompletionStat habitCompletionStat =
                    new HabitCompletionStat(event.habitId(), event.completedOn(), e.currentStreak(), e.completionCount());
                try {
                    repository.save(habitCompletionStat);
                } catch (DataIntegrityViolationException ex) {
                    logger.debug("Skipped duplicate completion event for habit {} on {}",
                            event.habitId(), event.completedOn());
                }
            }
            case HabitUncompletedEvent e -> { repository.deleteByHabitIdAndCompletedOn(e.habitId(), e.completedOn());
            }
        }
    }
}
