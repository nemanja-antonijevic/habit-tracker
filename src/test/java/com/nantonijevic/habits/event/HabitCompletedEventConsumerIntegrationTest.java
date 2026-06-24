package com.nantonijevic.habits.event;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.nantonijevic.habits.repository.HabitCompletionRepository;
import com.nantonijevic.habits.repository.HabitCompletionStatRepository;
import com.nantonijevic.habits.repository.HabitRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;

import java.time.LocalDate;
import java.util.concurrent.CountDownLatch;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

@EmbeddedKafka(topics = "habit-completed", partitions = 1)
@SpringBootTest(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.consumer.auto-offset-reset=earliest"
})
class HabitCompletedEventConsumerIntegrationTest {

    @Autowired
    private KafkaTemplate<String, HabitEvent> kafkaTemplate;

    @Autowired
    private HabitRepository repository;

    @Autowired
    private HabitCompletionStatRepository statRepository;

    @Autowired
    private HabitCompletionRepository completionRepository;

    @SpyBean
    private HabitCompletedEventConsumer consumer;

    private ListAppender<ILoggingEvent> logAppender;
    private CountDownLatch messagesProcessedLatch;

    @BeforeEach
    void setUp() {
        statRepository.deleteAll();

        messagesProcessedLatch = new CountDownLatch(2);

        doAnswer(invocation -> {
            try {
                return invocation.callRealMethod();
            } finally {
                messagesProcessedLatch.countDown();
            }
        }).when(consumer).on(any(HabitCompletedEvent.class));

        Logger consumerLogger = (Logger) LoggerFactory.getLogger(HabitCompletedEventConsumer.class);
        consumerLogger.setLevel(Level.DEBUG);

        logAppender = new ListAppender<>();
        logAppender.start();
        consumerLogger.addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        completionRepository.deleteAll();
        statRepository.deleteAll();
        repository.deleteAll();

        Logger consumerLogger = (Logger) LoggerFactory.getLogger(HabitCompletedEventConsumer.class);
        consumerLogger.detachAppender(logAppender);
    }

    @Test
    void duplicateEvent_isDedupedByUniqueConstraint() throws Exception {
        HabitCompletedEvent event = new HabitCompletedEvent(
                11L,
                LocalDate.now(),
                1,
                1
        );

        kafkaTemplate.send(
                "habit-completed",
                String.valueOf(event.habitId()),
                event
        ).get(10, SECONDS);

        kafkaTemplate.send(
                "habit-completed",
                String.valueOf(event.habitId()),
                event
        ).get(10, SECONDS);

        kafkaTemplate.flush();

        assertThat(messagesProcessedLatch.await(10, SECONDS)).isTrue();
        assertThat(statRepository.count()).isEqualTo(1);

        boolean skippedLogged = logAppender.list.stream()
                .anyMatch(e -> e.getFormattedMessage().contains("Skipped duplicate"));

        assertThat(skippedLogged).isTrue();
    }
}