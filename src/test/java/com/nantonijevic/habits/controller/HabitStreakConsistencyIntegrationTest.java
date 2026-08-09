package com.nantonijevic.habits.controller;

import com.nantonijevic.habits.domain.Habit;
import com.nantonijevic.habits.domain.HabitCompletionStat;
import com.nantonijevic.habits.repository.HabitCompletionStatRepository;
import com.nantonijevic.habits.repository.HabitWriteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumSet;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(properties = "spring.cache.type=none")
@AutoConfigureMockMvc
@Transactional
class HabitStreakConsistencyIntegrationTest {

    private static final ZoneId TEST_ZONE =
        ZoneId.of("UTC");

    private static final LocalDate TODAY =
        LocalDate.of(2026, 8, 10);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private HabitWriteRepository habitWriteRepository;

    @Autowired
    private HabitCompletionStatRepository completionStatRepository;

    @MockBean
    private Clock clock;

    @BeforeEach
    void useFixedMonday() {
        when(clock.getZone())
            .thenReturn(TEST_ZONE);

        when(clock.instant())
            .thenReturn(
                TODAY.atStartOfDay(TEST_ZONE).toInstant()
            );
    }

    @Test
    void statsEndpointsUseSamePreviousScheduledDayRule() throws Exception {
        Habit habit = new Habit("Read");
        habit.setScheduledDays(EnumSet.of(
            DayOfWeek.MONDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.FRIDAY
        ));

        Habit saved = habitWriteRepository.save(habit);

        completionStatRepository.save(
            new HabitCompletionStat(
                saved.getId(),
                LocalDate.of(2026, 8, 7),
                3,
                3
            )
        );

        assertAll(
            () -> mockMvc.perform(
                    get(
                        "/habits/"
                            + saved.getId()
                            + "/stats"
                    )
                )
                .andExpect(status().isOk())
                .andExpect(
                    jsonPath("$.currentStreak")
                        .value(3)
                ),

            () -> mockMvc.perform(
                    get("/habits/stats")
                )
                .andExpect(status().isOk())
                .andExpect(
                    jsonPath("$.activeStreaks")
                        .value(1)
                )
                .andExpect(
                    jsonPath("$.longestActiveStreak")
                        .value(3)
                )
        );
    }
}
