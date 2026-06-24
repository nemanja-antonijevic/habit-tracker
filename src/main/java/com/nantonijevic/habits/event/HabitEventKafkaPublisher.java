package com.nantonijevic.habits.event;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class HabitEventKafkaPublisher {

    private final KafkaTemplate<String, HabitEvent> kafkaTemplate;

    public HabitEventKafkaPublisher(KafkaTemplate<String, HabitEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(HabitEvent event) {
        kafkaTemplate.send("habit-completed", event.habitId().toString(), event);
    }

}
