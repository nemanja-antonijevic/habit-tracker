package com.nantonijevic.habits.event;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.nantonijevic.habits.repository.HabitCompletionStatRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;

import java.time.LocalDate;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@EmbeddedKafka(topics = "habit-completed", partitions = 1)
@SpringBootTest(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.consumer.auto-offset-reset=earliest"
})
class HabitCompletedEventConsumerIntegrationTest {

    @Autowired
    private KafkaTemplate<String, HabitCompletedEvent> kafkaTemplate;

    @Autowired
    private HabitCompletionStatRepository statRepository;

    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void attachLogAppender() {
        Logger consumerLogger = (Logger) LoggerFactory.getLogger(HabitCompletedEventConsumer.class);
        consumerLogger.setLevel(Level.DEBUG);   // poruka je DEBUG — moramo spustiti nivo da je uhvatimo
        logAppender = new ListAppender<>();
        logAppender.start();
        consumerLogger.addAppender(logAppender);
    }

    @Test
    void duplicateEvent_isDedupedByUniqueConstraint() {
        HabitCompletedEvent event = new HabitCompletedEvent(11L, LocalDate.now(), 1, 1);
        kafkaTemplate.send("habit-completed", String.valueOf(event.habitId()), event);
        kafkaTemplate.send("habit-completed", String.valueOf(event.habitId()), event);

        await().atMost(20, SECONDS).until(() -> statRepository.count() == 1);

        await().during(2, SECONDS)
                .atMost(5, SECONDS)
                .until(() -> statRepository.count() == 1);

        boolean skippedLogged = logAppender.list.stream()
                .anyMatch(e -> e.getFormattedMessage().contains("Skipped duplicate"));
        assertThat(skippedLogged).isTrue();
    }
}
