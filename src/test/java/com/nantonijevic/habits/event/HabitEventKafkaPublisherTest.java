package com.nantonijevic.habits.event;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class HabitEventKafkaPublisherTest {

    @Mock
    private KafkaTemplate<String, HabitEvent> kafkaTemplate;

    @Test
    void logsKafkaHandoffAfterSendingCommittedEvent() {
        HabitEventKafkaPublisher publisher =
            new HabitEventKafkaPublisher(kafkaTemplate);

        HabitCompletedEvent event =
            new HabitCompletedEvent(
                42L,
                LocalDate.of(2026, 1, 15),
                1,
                1
            );

        Logger logger = (Logger) LoggerFactory.getLogger(
            HabitEventKafkaPublisher.class
        );

        ListAppender<ILoggingEvent> logAppender =
            new ListAppender<>();

        logAppender.start();
        logger.addAppender(logAppender);

        try {
            publisher.on(event);

            verify(kafkaTemplate).send(
                "habit-completed",
                "42",
                event
            );

            assertThat(logAppender.list)
                .singleElement()
                .satisfies(logEvent -> {
                    assertThat(logEvent.getLevel())
                        .isEqualTo(Level.INFO);
                    assertThat(logEvent.getFormattedMessage())
                        .isEqualTo(
                            "Habit event handed to Kafka producer "
                                + "after commit, eventType: "
                                + "HabitCompletedEvent, habitId: 42"
                        );
                });
        } finally {
            logger.detachAppender(logAppender);
            logAppender.stop();
        }
    }
}
