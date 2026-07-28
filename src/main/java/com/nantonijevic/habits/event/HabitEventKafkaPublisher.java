package com.nantonijevic.habits.event;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class HabitEventKafkaPublisher {

    private static final Logger logger =
        LoggerFactory.getLogger(HabitEventKafkaPublisher.class);

    private final KafkaTemplate<String, HabitEvent> kafkaTemplate;

    public HabitEventKafkaPublisher(KafkaTemplate<String, HabitEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(HabitEvent event) {
        kafkaTemplate.send("habit-completed",
            event.habitId().toString(),
            event);

        logger.info(
            "Habit event handed to Kafka producer after commit, "
                + "eventType: {}, habitId: {}",
            event.getClass().getSimpleName(),
            event.habitId()
        );
    }

}
