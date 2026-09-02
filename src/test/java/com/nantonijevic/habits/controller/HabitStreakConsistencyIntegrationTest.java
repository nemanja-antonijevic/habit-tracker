package com.nantonijevic.habits.controller;

import com.nantonijevic.habits.client.ClientTierArgumentResolver;
import com.nantonijevic.habits.domain.Habit;
import com.nantonijevic.habits.domain.HabitCompletionStat;
import com.nantonijevic.habits.repository.HabitCompletionStatRepository;
import com.nantonijevic.habits.repository.HabitWriteRepository;
import com.nantonijevic.habits.support.InternalApiClientFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumSet;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(properties = "spring.cache.type=none")
@AutoConfigureMockMvc
@Transactional
@Import({
    HabitStreakConsistencyIntegrationTest.FixedClockConfiguration.class,
    InternalApiClientFixture.class
})
class HabitStreakConsistencyIntegrationTest {

    private static final ZoneId TEST_ZONE =
        ZoneId.of("UTC");

    private static final LocalDate TODAY =
        LocalDate.of(2026, 8, 10);

    private static final Instant TEST_INSTANT =
        TODAY.atStartOfDay(TEST_ZONE).toInstant();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private HabitWriteRepository habitWriteRepository;

    @Autowired
    private HabitCompletionStatRepository completionStatRepository;

    @Autowired
    private Clock clock;

    @Autowired
    private InternalApiClientFixture apiClientFixture;

    private String apiKey;
    private Long ownerId;

    @BeforeEach
    void provisionInternalClient() {
        var client =
            apiClientFixture
                .provisionInternalClientWithIdentity();
        apiKey = client.apiKey();
        ownerId = client.clientId();
    }

    private ResultActions perform(
        MockHttpServletRequestBuilder request
    ) throws Exception {

        return mockMvc.perform(
            request.header(
                ClientTierArgumentResolver.API_KEY_HEADER,
                apiKey
            )
        );
    }

    @Test
    void statsEndpointsUseSamePreviousScheduledDayRule() throws Exception {
        Habit habit = new Habit(ownerId, "Read", clock.instant());
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
            () -> perform(
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

            () -> perform(
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

    @TestConfiguration
    static class FixedClockConfiguration {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(TEST_INSTANT, TEST_ZONE);
        }
    }
}
