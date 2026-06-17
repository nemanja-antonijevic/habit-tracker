package com.nantonijevic.habits.event;

import com.nantonijevic.habits.domain.HabitCompletionStat;
import com.nantonijevic.habits.repository.HabitCompletionStatRepository;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class HabitCompletedEventConsumer {

    private final HabitCompletionStatRepository repository;

    public HabitCompletedEventConsumer(HabitCompletionStatRepository repository) {
        this.repository = repository;
    }

    @KafkaListener(topics = "habit-completed", groupId = "habit-stats")
    public void on(HabitCompletedEvent event) {
        HabitCompletionStat habitCompletionStat =
                new HabitCompletionStat(event.habitId(), event.completedOn(), event.currentStreak(), event.completionCount());

        repository.save(habitCompletionStat);
    }
}
