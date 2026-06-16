package com.nantonijevic.habits.event;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class HabitEventKafkaPublisher {

    private final KafkaTemplate<String, HabitCompletedEvent> kafkaTemplate;

    public HabitEventKafkaPublisher(KafkaTemplate<String, HabitCompletedEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(HabitCompletedEvent event) {
        kafkaTemplate.send("habit-completed", event.habitId().toString(), event);
    }

}
