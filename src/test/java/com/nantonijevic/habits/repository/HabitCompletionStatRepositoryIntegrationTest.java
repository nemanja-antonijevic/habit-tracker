package com.nantonijevic.habits.repository;

import com.nantonijevic.habits.dto.HabitStatsView;
import com.nantonijevic.habits.event.HabitCompletedEvent;
import com.nantonijevic.habits.event.HabitCompletedEventConsumer;
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
    private KafkaTemplate<String, HabitCompletedEvent> kafkaTemplate;

    @Autowired
    private HabitCompletionStatRepository statRepository;

    @SpyBean
    private HabitCompletedEventConsumer consumer;

    private CountDownLatch messagesProcessedLatch;

    @BeforeEach
    void setUp() {
        statRepository.deleteAll();

        doAnswer(invocation -> {
            try {
                return invocation.callRealMethod();
            } finally {
                if (messagesProcessedLatch != null) {
                    messagesProcessedLatch.countDown();
                }
            }
        }).when(consumer).on(any(HabitCompletedEvent.class));
    }

    @Test
    void aggregatesStatsAcrossMultipleCompletionEvents() throws Exception {
        messagesProcessedLatch = new CountDownLatch(3);

        Long habitId = 11L;
        LocalDate today = LocalDate.now();

        sendCompletedEvent(habitId, today.minusDays(2), 1, 1);
        sendCompletedEvent(habitId, today.minusDays(1), 2, 2);
        sendCompletedEvent(habitId, today, 3, 3);

        kafkaTemplate.flush();

        assertThat(messagesProcessedLatch.await(10, SECONDS)).isTrue();

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

    private void sendCompletedEvent(
            Long habitId,
            LocalDate completedDate,
            int currentStreak,
            int completionCount
    ) throws Exception {
        HabitCompletedEvent event = new HabitCompletedEvent(
                habitId,
                completedDate,
                currentStreak,
                completionCount
        );

        kafkaTemplate.send(
                "habit-completed",
                String.valueOf(event.habitId()),
                event
        ).get(10, SECONDS);
    }
}