package com.nantonijevic.habits.controller;

import com.nantonijevic.habits.domain.Habit;
import com.nantonijevic.habits.event.HabitCompletedEvent;
import com.nantonijevic.habits.event.HabitCompletedEventConsumer;
import com.nantonijevic.habits.repository.HabitCompletionStatRepository;
import com.nantonijevic.habits.repository.HabitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.concurrent.CountDownLatch;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


// TODO(Day 27): unlock the @Disabled tests one by one. Before that, the test bodies need fixing:
//   1. @Disabled reasons are stale (copied from HabitControllerIntegrationTest) — they say
//      "no @EmbeddedKafka", but this class HAS it. Rewrite each reason to the real blocker
//      (e.g. "awaiting per-test latch wiring"; for uncomplete_decrementsOnlyByOne: HabitUncompletedEvent).
//   2. getStats_returnsCorrectCountAndTimestamp uses jsonPath "$.lastCompletedAt" — field was
//      renamed to "lastCompletedOn" (Day 25). Fix the path.
//   3. CQRS mismatch: tests call habit.complete(...) directly on the entity, which does NOT go
//      through the service and emits NO Kafka event — the read-model stays empty for those days.
//      To seed N days into the read-model, send N events (N× POST /complete with distinct dates,
//      or publish to Kafka like HabitCompletedEventConsumerIntegrationTest), not write-side
//      habit.complete(). Affects the currentStreak==3 / longestStreak==3 expectations.
//   4. CountDownLatch(2) in setUp() must become per-test — each test sends a different number of
//      events; create the latch inside each test once unlocked.
@EmbeddedKafka(topics = "habit-completed", partitions = 1)
@SpringBootTest(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.consumer.auto-offset-reset=earliest"
})
@AutoConfigureMockMvc
public class HabitStatsIntegrationTest {

    @Autowired
    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    private MockMvc mockMvc;

    @Autowired
    private HabitRepository repository;

    @Autowired
    private HabitCompletionStatRepository statRepository;

    @Autowired
    private KafkaTemplate<String, HabitCompletedEvent> kafkaTemplate;

    private CountDownLatch messagesProcessedLatch;

    @SpyBean
    private HabitCompletedEventConsumer consumer;

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
    }

    @Test
    void getStats_returnsCorrectCountAndTimestamp_afterComplete() throws Exception {
        messagesProcessedLatch = new CountDownLatch(1);
        Habit saved = repository.save(new Habit("Read 30 min"));
        mockMvc.perform(post("/habits/" + saved.getId() + "/complete"))
                .andExpect(status().isOk());
        assertThat(messagesProcessedLatch.await(10, SECONDS)).isTrue();
        mockMvc.perform(get("/habits/" + saved.getId() + "/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completionCount").value(1))
                .andExpect(jsonPath("$.lastCompletedOn").isNotEmpty());
    }

    @Test
    void getStats_returnsCurrentStreak_afterConsecutiveCompletions() throws Exception {
        messagesProcessedLatch = new CountDownLatch(3);

        var habit = repository.save(new Habit("Read"));
        LocalDate today = LocalDate.now();

        kafkaTemplate.send(
                "habit-completed",
                String.valueOf(habit.getId()),
                new HabitCompletedEvent(habit.getId(), today.minusDays(2), 1, 1)
        ).get(10, SECONDS);

        kafkaTemplate.send(
                "habit-completed",
                String.valueOf(habit.getId()),
                new HabitCompletedEvent(habit.getId(), today.minusDays(1), 2, 2)
        ).get(10, SECONDS);

        kafkaTemplate.send(
                "habit-completed",
                String.valueOf(habit.getId()),
                new HabitCompletedEvent(habit.getId(), today, 3, 3)
        ).get(10, SECONDS);

        assertThat(messagesProcessedLatch.await(10, SECONDS)).isTrue();

        mockMvc.perform(get("/habits/" + habit.getId() + "/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStreak").value(3));
    }

    @Test
    @Disabled("Dva uzroka: read-model prazan bez @EmbeddedKafka + uncomplete ne emituje event (read-model se ne smanjuje). Treba async setup + HabitUncompletedEvent. Vidi reflection Day 25.")
    void uncomplete_decrementsOnlyByOne() throws Exception {
        var habit = new Habit("Read 30 min");
        habit.complete(LocalDate.now().minusDays(2));
        habit.complete(LocalDate.now().minusDays(1));
        habit.complete(LocalDate.now());
        var saved = repository.save(habit);

        mockMvc.perform(post("/habits/" + saved.getId() + "/uncomplete"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completionCount").value(2));

        mockMvc.perform(get("/habits/" + saved.getId() + "/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completionCount").value(2));
    }

    @Test
    void firstCompleteSetsLongestStreakToOne() throws Exception {
        messagesProcessedLatch = new CountDownLatch(1);
        var habit = new Habit("Read 30 min");
        repository.save(habit);
        mockMvc.perform(post("/habits/" + habit.getId() + "/complete"))
                .andExpect(status().isOk());
        assertThat(messagesProcessedLatch.await(10, SECONDS)).isTrue();
        mockMvc.perform(get("/habits/" + habit.getId() + "/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStreak").value(1))
                .andExpect(jsonPath("$.longestStreak").value(1));
    }

    @Test
    void streakResetDoesNotLowerLongestStreak() throws Exception {
        messagesProcessedLatch = new CountDownLatch(4);

        var habit = repository.save(new Habit("Read 30 min"));
        LocalDate today = LocalDate.now();

        kafkaTemplate.send(
                "habit-completed",
                String.valueOf(habit.getId()),
                new HabitCompletedEvent(habit.getId(), today.minusDays(4), 1, 1)
        ).get(10, SECONDS);

        kafkaTemplate.send(
                "habit-completed",
                String.valueOf(habit.getId()),
                new HabitCompletedEvent(habit.getId(), today.minusDays(3), 2, 2)
        ).get(10, SECONDS);

        kafkaTemplate.send(
                "habit-completed",
                String.valueOf(habit.getId()),
                new HabitCompletedEvent(habit.getId(), today.minusDays(2), 3, 3)
        ).get(10, SECONDS);

        kafkaTemplate.send(
                "habit-completed",
                String.valueOf(habit.getId()),
                new HabitCompletedEvent(habit.getId(), today, 1, 4)
        ).get(10, SECONDS);

        assertThat(messagesProcessedLatch.await(10, SECONDS)).isTrue();

        mockMvc.perform(get("/habits/" + habit.getId() + "/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStreak").value(1))
                .andExpect(jsonPath("$.longestStreak").value(3));
    }

}
