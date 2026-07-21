package com.nantonijevic.habits.controller;

import com.nantonijevic.habits.domain.Habit;
import com.nantonijevic.habits.event.HabitCompletedEvent;
import com.nantonijevic.habits.event.HabitCompletedEventConsumer;
import com.nantonijevic.habits.event.HabitEvent;
import com.nantonijevic.habits.repository.HabitCompletionRepository;
import com.nantonijevic.habits.repository.HabitCompletionStatRepository;
import com.nantonijevic.habits.repository.HabitWriteRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.web.servlet.MockMvc;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.EnumSet;
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
    private HabitWriteRepository habitWriteRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private HabitCompletionStatRepository statRepository;

    @Autowired
    private HabitCompletionRepository completionRepository;

    @Autowired
    private KafkaTemplate<String, HabitEvent> kafkaTemplate;

    @SpyBean
    private HabitCompletedEventConsumer consumer;

    private CountDownLatch messagesProcessedLatch;

    @BeforeEach
    void setUp() {
        completionRepository.deleteAll();
        statRepository.deleteAll();
        jdbcTemplate.update("DELETE FROM habits");

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
        jdbcTemplate.update("DELETE FROM habits");
    }

    @Test
    void getStats_returnsCorrectCountAndTimestamp_afterComplete() throws Exception {
        expectEvents(1);

        Habit saved = habitWriteRepository.save(new Habit("Read 30 min"));

        mockMvc.perform(post("/habits/" + saved.getId() + "/complete"))
                .andExpect(status().isOk());

        awaitEvents();

        mockMvc.perform(get("/habits/" + saved.getId() + "/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completionCount").value(1))
                .andExpect(jsonPath("$.lastCompletedOn").isNotEmpty());
    }

    @Test
    void getStats_returnsCurrentStreak_afterConsecutiveCompletions() throws Exception {
        expectEvents(3);

        var habit = habitWriteRepository.save(new Habit("Read"));
        LocalDate today = LocalDate.now();

        publishCompletedEvent(habit.getId(), today.minusDays(2), 1, 1);
        publishCompletedEvent(habit.getId(), today.minusDays(1), 2, 2);
        publishCompletedEvent(habit.getId(), today, 3, 3);

        awaitEvents();

        mockMvc.perform(get("/habits/" + habit.getId() + "/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStreak").value(3));
    }

    @Test
    void uncomplete_decrementsOnlyByOne() throws Exception {
        expectEvents(3);

        LocalDate today = LocalDate.now();

        var habit = new Habit("Read 30 min");
        habit.complete(today.minusDays(2));
        habit.complete(today.minusDays(1));
        habit.complete(today);

        var saved = habitWriteRepository.save(habit);

        publishCompletedEvent(saved.getId(), today.minusDays(2), 1, 1);
        publishCompletedEvent(saved.getId(), today.minusDays(1), 2, 2);
        publishCompletedEvent(saved.getId(), today, 3, 3);

        awaitEvents();

        expectEvents(1);

        mockMvc.perform(post("/habits/" + saved.getId() + "/uncomplete"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completionCount").value(2));

        awaitEvents();

        mockMvc.perform(get("/habits/" + saved.getId() + "/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completionCount").value(2));
    }

    @Test
    void firstCompleteSetsLongestStreakToOne() throws Exception {
        expectEvents(1);

        var habit = new Habit("Read 30 min");
        habitWriteRepository.save(habit);

        mockMvc.perform(post("/habits/" + habit.getId() + "/complete"))
                .andExpect(status().isOk());

        awaitEvents();

        mockMvc.perform(get("/habits/" + habit.getId() + "/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStreak").value(1))
                .andExpect(jsonPath("$.longestStreak").value(1));
    }

    @Test
    void streakResetDoesNotLowerLongestStreak() throws Exception {
        expectEvents(4);

        var habit = habitWriteRepository.save(new Habit("Read 30 min"));
        LocalDate today = LocalDate.now();

        publishCompletedEvent(habit.getId(), today.minusDays(4), 1, 1);
        publishCompletedEvent(habit.getId(), today.minusDays(3), 2, 2);
        publishCompletedEvent(habit.getId(), today.minusDays(2), 3, 3);
        publishCompletedEvent(habit.getId(), today, 1, 4);

        awaitEvents();

        mockMvc.perform(get("/habits/" + habit.getId() + "/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStreak").value(1))
                .andExpect(jsonPath("$.longestStreak").value(3));
    }

    @Test
    void getStats_keepsCurrentStreakAliveAcrossOffDays() throws Exception {
        expectEvents(1);

        LocalDate today = LocalDate.now();

        var scheduledDays = EnumSet.of(
                DayOfWeek.MONDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.FRIDAY
        );

        LocalDate previousScheduledDay = today.minusDays(1);
        while (!scheduledDays.contains(previousScheduledDay.getDayOfWeek())) {
            previousScheduledDay = previousScheduledDay.minusDays(1);
        }

        var habit = new Habit("Workout");
        habit.setScheduledDays(scheduledDays);
        var saved = habitWriteRepository.save(habit);

        publishCompletedEvent(saved.getId(), previousScheduledDay, 1, 1);

        awaitEvents();

        mockMvc.perform(get("/habits/" + saved.getId() + "/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStreak").value(1));
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

    @Test
    void getDashboardStats_aggregatesOnlyActiveHabits() throws Exception {
        LocalDate today = LocalDate.now();
        DayOfWeek todayDay = today.getDayOfWeek();
        DayOfWeek tomorrowDay = today.plusDays(1).getDayOfWeek();

        var completedDueHabit = new Habit("Completed today");
        completedDueHabit.setScheduledDays(EnumSet.of(todayDay));
        habitWriteRepository.save(completedDueHabit);

        var incompleteDueHabit = new Habit("Still due today");
        incompleteDueHabit.setScheduledDays(EnumSet.of(todayDay));
        habitWriteRepository.save(incompleteDueHabit);

        var notDueTodayHabit = new Habit("Not due today");
        notDueTodayHabit.setScheduledDays(EnumSet.of(tomorrowDay));
        habitWriteRepository.save(notDueTodayHabit);

        var archivedHabit = new Habit("Archived habit");
        archivedHabit.setScheduledDays(EnumSet.of(todayDay));
        habitWriteRepository.save(archivedHabit);

        expectEvents(2);

        mockMvc.perform(post("/habits/" + completedDueHabit.getId() + "/complete"))
            .andExpect(status().isOk());

        mockMvc.perform(post("/habits/" + archivedHabit.getId() + "/complete"))
            .andExpect(status().isOk());

        awaitEvents();

        mockMvc.perform(post("/habits/" + archivedHabit.getId() + "/archive"))
            .andExpect(status().isOk());

        mockMvc.perform(get("/habits/stats"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.dueToday").value(2))
            .andExpect(jsonPath("$.completedToday").value(1))
            .andExpect(jsonPath("$.activeStreaks").value(1))
            .andExpect(jsonPath("$.longestActiveStreak").value(1))
            .andExpect(jsonPath("$.totalHabits").value(3));
    }

    @Test
    void getDashboardStats_returnsZerosWhenThereAreNoActiveHabits() throws Exception {
        mockMvc.perform(get("/habits/stats"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.dueToday").value(0))
            .andExpect(jsonPath("$.completedToday").value(0))
            .andExpect(jsonPath("$.activeStreaks").value(0))
            .andExpect(jsonPath("$.longestActiveStreak").value(0))
            .andExpect(jsonPath("$.totalHabits").value(0));
    }

    @Test
    void getDashboardStats_returnsZeroLongestStreakWhenNoActiveStreaksExist() throws Exception {
        LocalDate today = LocalDate.now();

        var firstHabit = habitWriteRepository.save(new Habit("Read"));
        var secondHabit = habitWriteRepository.save(new Habit("Workout"));

        expectEvents(2);

        publishCompletedEvent(firstHabit.getId(), today.minusDays(3), 5, 5);
        publishCompletedEvent(secondHabit.getId(), today.minusDays(4), 2, 2);

        awaitEvents();

        mockMvc.perform(get("/habits/stats"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.dueToday").value(2))
            .andExpect(jsonPath("$.completedToday").value(0))
            .andExpect(jsonPath("$.activeStreaks").value(0))
            .andExpect(jsonPath("$.longestActiveStreak").value(0))
            .andExpect(jsonPath("$.totalHabits").value(2));
    }

    @Test
    void getDashboardStats_returnsLongestAmongMultipleActiveStreaks() throws Exception {
        LocalDate today = LocalDate.now();

        var threeDayStreakHabit = habitWriteRepository.save(
            new Habit("Three day streak")
        );

        var sevenDayStreakHabit = habitWriteRepository.save(
            new Habit("Seven day streak")
        );

        expectEvents(2);

        publishCompletedEvent(
            threeDayStreakHabit.getId(),
            today,
            3,
            3
        );

        publishCompletedEvent(
            sevenDayStreakHabit.getId(),
            today,
            7,
            7
        );

        awaitEvents();

        mockMvc.perform(get("/habits/stats"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.activeStreaks").value(2))
            .andExpect(jsonPath("$.longestActiveStreak").value(7))
            .andExpect(jsonPath("$.totalHabits").value(2));
    }
}
