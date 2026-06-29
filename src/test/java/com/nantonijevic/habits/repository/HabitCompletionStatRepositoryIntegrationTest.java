package com.nantonijevic.habits.repository;

import com.nantonijevic.habits.dto.HabitStatsView;
import com.nantonijevic.habits.event.HabitCompletedEvent;
import com.nantonijevic.habits.event.HabitCompletedEventConsumer;
import com.nantonijevic.habits.event.HabitEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
class HabitCompletionStatRepositoryIntegrationTest {

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

    private CountDownLatch messagesProcessedLatch;

    @BeforeEach
    void setUp() {
        completionRepository.deleteAll();
        statRepository.deleteAll();
        repository.deleteAll();

        doAnswer(invocation -> {
            try {
                return invocation.callRealMethod();
            } finally {
                if (messagesProcessedLatch != null) {
                    messagesProcessedLatch.countDown();
                }
            }
        }).when(consumer).on(any(HabitEvent.class));
    }

    @AfterEach
    void tearDown() {
        completionRepository.deleteAll();
        statRepository.deleteAll();
        repository.deleteAll();
    }

    @Test
    void aggregatesStatsAcrossMultipleCompletionEvents() throws Exception {
        expectEvents(3);

        Long habitId = 11L;
        LocalDate today = LocalDate.now();

        publishCompletedEvent(habitId, today.minusDays(2), 1, 1);
        publishCompletedEvent(habitId, today.minusDays(1), 2, 2);
        publishCompletedEvent(habitId, today, 3, 3);

        awaitEvents();

        HabitStatsView view = statRepository.findStatsByHabitId(habitId);

        assertThat(view).satisfies(stats -> {
            assertThat(stats.completionCount()).isEqualTo(3);
            assertThat(stats.longestStreak()).isEqualTo(3);
            assertThat(stats.lastCompletedOn()).isEqualTo(today);
        });
    }

    @Test
    void returnsEmptyStatsWhenNoCompletionEventsWereProcessed() {
        HabitStatsView view = statRepository.findStatsByHabitId(999L);

        assertThat(view).satisfies(stats -> {
            assertThat(stats.completionCount()).isZero();
            assertThat(stats.longestStreak()).isNull();
            assertThat(stats.lastCompletedOn()).isNull();
        });
    }

    private void expectEvents(int count) {
        messagesProcessedLatch = new CountDownLatch(count);
    }

    private void awaitEvents() throws InterruptedException {
        assertThat(messagesProcessedLatch.await(10, SECONDS)).isTrue();
    }

    private void publishCompletedEvent(
            Long habitId,
            LocalDate completedDate,
            int currentStreak,
            int completionCount
    ) throws Exception {
        kafkaTemplate.send(
                "habit-completed",
                String.valueOf(habitId),
                new HabitCompletedEvent(habitId, completedDate, currentStreak, completionCount)
        ).get(10, SECONDS);
    }
}