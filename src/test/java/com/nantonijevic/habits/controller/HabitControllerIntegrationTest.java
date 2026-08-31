package com.nantonijevic.habits.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nantonijevic.habits.client.ClientTierArgumentResolver;
import com.nantonijevic.habits.domain.Habit;
import com.nantonijevic.habits.domain.HabitCompletion;
import com.nantonijevic.habits.domain.HabitCompletionStat;
import com.nantonijevic.habits.dto.CreateHabitRequest;
import com.nantonijevic.habits.AbstractIntegrationTest;
import com.nantonijevic.habits.repository.HabitCompletionRepository;
import com.nantonijevic.habits.repository.HabitCompletionStatRepository;
import com.nantonijevic.habits.support.HabitTestFixtureRepository;
import com.nantonijevic.habits.support.InternalApiClientFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;


import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumSet;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(InternalApiClientFixture.class)
@Transactional
@AutoConfigureMockMvc
class HabitControllerIntegrationTest extends AbstractIntegrationTest {

    // Deliberately ambient: these tests assert relative day transitions
    // (now minus N days) in whatever zone TimeConfig resolves to, so fixture
    // and production read the same zone by design. Pinning it would swap one
    // ambient source for a hardcoded one without adding proof.
    private static final ZoneId APPLICATION_ZONE =
        ZoneId.systemDefault();

    private static final Instant FIXED =
        Instant.parse("2026-08-14T10:00:00Z");

    @Autowired
    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private HabitTestFixtureRepository repository;

    @Autowired
    private HabitCompletionRepository completionRepository;

    @Autowired
    private HabitCompletionStatRepository completionStatRepository;

    @Autowired
    private InternalApiClientFixture apiClientFixture;

    private String apiKey;

    @BeforeEach
    void provisionInternalClient() {
        apiKey = apiClientFixture.provisionInternalClient();
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
    void createHabit_returns201_andPersistsHabit() throws Exception {
        var request = new CreateHabitRequest("Code 3 hours", null);

        perform(post("/habits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("Code 3 hours"))
                .andExpect(jsonPath("$.scheduledDays").isArray())
                .andExpect(jsonPath("$.scheduledDays.length()").value(7));
    }

    @Test
    void createHabit_returnsProvidedScheduledDays() throws Exception {
        String jsonBody = """
            {
              "name": "Workout",
              "scheduledDays": ["MONDAY", "WEDNESDAY", "FRIDAY"]
            }
            """;

        perform(post("/habits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Workout"))
                .andExpect(jsonPath("$.scheduledDays").isArray())
                .andExpect(jsonPath("$.scheduledDays.length()").value(3))
                .andExpect(jsonPath("$.scheduledDays", containsInAnyOrder(
                        "MONDAY",
                        "WEDNESDAY",
                        "FRIDAY"
                )));
    }

    @Test
    void createHabit_rejectsEmptyScheduledDays() throws Exception {
        String jsonBody = """
            {
              "name": "Workout",
              "scheduledDays": []
            }
            """;

        perform(post("/habits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createHabit_returns400_whenRequestBodyIsEmpty() throws Exception {
        perform(post("/habits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Malformed or missing request body"));
    }

    @Test
    void createHabit_returns400_whenNameIsBlank() throws Exception {
        var request = new CreateHabitRequest("", null);

        perform(post("/habits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("name must not be blank"));
    }

    @Test
    void createHabit_returns400_whenNameIsTooLong() throws Exception {
        var request = new CreateHabitRequest("a".repeat(256), null);
        perform(post("/habits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("name size must be between 0 and 255"));
    }

    @Test
    void createHabit_returns400_whenNameIsWhitespaceOnly() throws Exception {
        var request = new CreateHabitRequest("    ", null);
        perform(post("/habits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("name must not be blank"));
    }

    @Test
    void listHabits_returnsEmptyArray_whenNoHabits() throws Exception {
        perform(get("/habits"))
                .andExpect(status().is(200))
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void listHabits_returnsOneHabit_whenOneExists() throws Exception {
        var request = new CreateHabitRequest("Code 3 hours", null);

        perform(post("/habits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("Code 3 hours"));

        perform(get("/habits"))
                .andExpect(status().is(200))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").exists())
                .andExpect(jsonPath("$.content[0].name").value("Code 3 hours"))
                .andExpect(jsonPath("$.content[0].archived").value(false));
    }

    @Test
    void listHabits_returnsNewestFirst_byDefault() throws Exception {
        repository.save(
            new Habit("Swim", FIXED)
        );
        repository.save(
            new Habit(
                "Gym",
                FIXED.plusSeconds(1)
            )
        );

        perform(get("/habits"))
            .andExpect(status().is(200))
            .andExpect(jsonPath("$.content.length()").value(2))
            .andExpect(jsonPath("$.content[0].id").exists())
            .andExpect(jsonPath("$.content[0].name").value("Gym"))
            .andExpect(jsonPath("$.content[0].archived").value(false))
            .andExpect(jsonPath("$.content[1].id").exists())
            .andExpect(jsonPath("$.content[1].name").value("Swim"))
            .andExpect(jsonPath("$.content[1].archived").value(false));
    }

    @Test
    void listHabits_returnsHabitByName_whenQueryIsCorrect() throws Exception {
        repository.save(new Habit("Swim", FIXED));
        repository.save(new Habit("Gym", FIXED));

        perform(get("/habits?name=Swim"))
                .andExpect(status().is(200))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").exists())
                .andExpect(jsonPath("$.content[0].name").value("Swim"))
                .andExpect(jsonPath("$.content[0].archived").value(false));
    }

    @Test
    void listHabits_returnsHabitByName_whenMixcased() throws Exception {
        repository.save(new Habit("Swim", FIXED));
        repository.save(new Habit("Gym", FIXED));

        perform(get("/habits?name=SwIm"))
                .andExpect(status().is(200))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").exists())
                .andExpect(jsonPath("$.content[0].name").value("Swim"))
                .andExpect(jsonPath("$.content[0].archived").value(false));
    }

    @Test
    void listHabits_returnsHabitByName_whenNameHasTrailingSpace() throws Exception {
        repository.save(new Habit("Swim", FIXED));
        repository.save(new Habit("Gym", FIXED));

        perform(get("/habits").param("name", "Swim  "))
                .andExpect(status().is(200))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").exists())
                .andExpect(jsonPath("$.content[0].name").value("Swim"))
                .andExpect(jsonPath("$.content[0].archived").value(false));
    }

    @Test
    void listHabits_returnsHabitByName_whenNamePartiallyMatches() throws Exception {
        repository.save(new Habit("Swim", FIXED));

        perform(get("/habits?name=wi"))
                .andExpect(status().is(200))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").exists())
                .andExpect(jsonPath("$.content[0].name").value("Swim"))
                .andExpect(jsonPath("$.content[0].archived").value(false));
    }

    @Test
    void listHabits_returnsAllHabits_whenNameIsEmpty() throws Exception {
        repository.save(new Habit("Swim", FIXED));
        repository.save(new Habit("Gym", FIXED));

        perform(get("/habits?name="))
                .andExpect(status().is(200))
                .andExpect(jsonPath("$.content.length()").value(2));
    }

    @Test
    void listHabits_returnsAllHabits_whenNameIsWhitespace() throws Exception {
        repository.save(new Habit("Swim", FIXED));
        repository.save(new Habit("Gym", FIXED));

        perform(get("/habits").param("name", "    "))
                .andExpect(status().is(200))
                .andExpect(jsonPath("$.content.length()").value(2));
    }

    @Test
    void listHabits_returnsNoHabitsByName_whenNameDoesNotMatch() throws Exception {
        repository.save(new Habit("Swim", FIXED));
        repository.save(new Habit("Gym", FIXED));

        perform(get("/habits?name=Run"))
                .andExpect(status().is(200))
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void listHabits_excludesArchivedMatch_whenIncludeArchivedFalse() throws Exception {
        var habit = new Habit("Read", FIXED);

        repository.save(habit);

        perform(post("/habits/" + habit.getId() + "/archive"))
                .andExpect(status().isOk());
        perform(get("/habits?name=Read"))
                .andExpect(status().is(200))
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void listHabits_includesArchivedMatch_whenIncludeArchivedTrue() throws Exception {
        var habit = new Habit("Read", FIXED);

        repository.save(habit);

        perform(post("/habits/" + habit.getId() + "/archive"))
                .andExpect(status().isOk());

        perform(get("/habits?name=Read&includeArchived=true"))
                .andExpect(status().is(200))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").exists())
                .andExpect(jsonPath("$.content[0].name").value("Read"))
                .andExpect(jsonPath("$.content[0].archived").value(true));
    }

    @Test
    void listHabits_returnsZeroCurrentStreak_whenLastCompletionIsOlderThanYesterday() throws Exception {
        var habit = new Habit("Read", FIXED);

        habit.complete(
            LocalDate.now(APPLICATION_ZONE).minusDays(3),
            APPLICATION_ZONE
        );
        habit.complete(
            LocalDate.now(APPLICATION_ZONE).minusDays(2),
            APPLICATION_ZONE
        );

        repository.save(habit);

        perform(get("/habits"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Read"))
                .andExpect(jsonPath("$.content[0].currentStreak").value(0));

        var reloaded = repository.findById(habit.getId()).orElseThrow();
        assertThat(reloaded.getCurrentStreak()).isEqualTo(2);
    }

    @Test
    void getHabit_returnsHabit_whenExists() throws Exception {
        var saved = repository.save(new Habit("Code 3 hours", FIXED));

        perform(get("/habits/" + saved.getId()))
                .andExpect(status().is(200))
                .andExpect(jsonPath("$.id").value(saved.getId()))
                .andExpect(jsonPath("$.name").value("Code 3 hours"))
                .andExpect(jsonPath("$.archived").value(false));
    }

    @Test
    void getHabit_returns404_whenNotExists() throws Exception {
        perform(get("/habits/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Habit not found: " + 999999));
    }

    @Test
    void getHabit_returnsCorrectHabit_whenMultipleExists() throws Exception {
        var saved1 = repository.save(new Habit("Sport", FIXED));

        perform(get("/habits/" + saved1.getId()))
                .andExpect(status().is(200))
                .andExpect(jsonPath("$.id").value(saved1.getId()))
                .andExpect(jsonPath("$.name").value("Sport"))
                .andExpect(jsonPath("$.archived").value(false));
    }

    @Test
    void getHabit_returnsZeroCurrentStreak_whenLastCompletionIsOlderThanYesterday() throws Exception {
        var habit = new Habit("Read", FIXED);

        habit.complete(
            LocalDate.now(APPLICATION_ZONE).minusDays(3),
            APPLICATION_ZONE
        );
        habit.complete(
            LocalDate.now(APPLICATION_ZONE).minusDays(2),
            APPLICATION_ZONE
        );

        var saved = repository.save(habit);

        perform(get("/habits/" + saved.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStreak").value(0));

        var reloaded = repository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getCurrentStreak()).isEqualTo(2);
    }

    @Test
    void getHabit_returnsCurrentStreak_whenLastCompletionWasYesterday() throws Exception {
        var habit = new Habit("Read", FIXED);

        habit.complete(
            LocalDate.now(APPLICATION_ZONE).minusDays(2),
            APPLICATION_ZONE
        );
        habit.complete(
            LocalDate.now(APPLICATION_ZONE).minusDays(1),
            APPLICATION_ZONE
        );

        var saved = repository.save(habit);

        perform(get("/habits/" + saved.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStreak").value(2));

        var reloaded = repository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getCurrentStreak()).isEqualTo(2);
    }

    @Test
    void deleteHabit_returns204_andRemovesHabit() throws Exception {
        var saved = repository.save(new Habit("Code 3 hours", FIXED));

        perform(delete("/habits/" + saved.getId()))
                .andExpect(status().isNoContent());
        perform(get("/habits/" + saved.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Habit not found: " + saved.getId()));
    }

    @Test
    void deleteHabit_returns404_whenNotExists() throws Exception {
        perform(delete("/habits/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Habit not found: " + 999999));
    }

    @Test
    void updateHabit_returns200_andUpdatesHabit() throws Exception {
        var saved = repository.save(new Habit("Code 3 hours", FIXED));
        String jsonBody = "{\"name\": \"Code 6 hours\",\"version\": 0}";

        perform(put("/habits/" + saved.getId()).content(jsonBody).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(saved.getId()))
                .andExpect(jsonPath("$.name").value("Code 6 hours"))
                .andExpect(jsonPath("$.archived").value(false));
    }

    @Test
    void updateHabit_returns404_whenNotExists() throws Exception {
        String jsonBody = "{\"name\": \"Code 6 hours\",\"version\": 0}";

        perform(put("/habits/999999").content(jsonBody).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Habit not found: " + 999999));
    }

    @Test
    void updateHabit_returns400_whenBodyIsEmpty() throws Exception {
        perform(put("/habits/999999").content("").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Malformed or missing request body"));
    }

    @Test
    void updateHabit_returns400_whenNameIsBlank_evenIfIdNotFound() throws Exception {
        String jsonBody = "{\"name\": \"\"}";

        perform(put("/habits/999999").content(jsonBody).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("name must not be blank")));
    }


    @Test
    void updateHabit_returns400_whenNameIsTooLong() throws Exception {
        String jsonBody = "{\"name\": \"" + "a".repeat(256) + "\"}";

        perform(put("/habits/999999").content(jsonBody).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error")
                        .value(containsString("name size must be between 0 and 255")));
    }

    @Test
    void updateHabit_returns400_whenNameIsWhitespaceOnly() throws Exception {
        String jsonBody = "{\"name\": \"        \"}";

        perform(put("/habits/999999").content(jsonBody).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("name must not be blank")));
    }

    @Test
    void updateHabit_returns200_whenVersionMatches() throws Exception {
        var saved = repository.save(new Habit("Code 3 hours", FIXED));

        String jsonBody = "{\"name\": \"Sleep\",\"version\": 0}";

        perform(put("/habits/" + saved.getId()).content(jsonBody).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        perform(get("/habits/" + saved.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(saved.getId()))
                .andExpect(jsonPath("$.name").value("Sleep"))
                .andExpect(jsonPath("$.archived").value(false));
    }

    @Test
    void updateHabit_returns409_whenVersionStale() throws Exception {
        var saved = repository.save(new Habit("Code 3 hours", FIXED));
        String jsonBody1 = "{\"name\": \"Sleep\",\"version\": 0}";
        String jsonBody2 = "{\"name\": \"Eat\",\"version\": 0}";

        perform(put("/habits/" + saved.getId()).content(jsonBody1).contentType(MediaType.APPLICATION_JSON));
        perform(put("/habits/" + saved.getId()).content(jsonBody2).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value(containsString("Habit version conflict")));
    }

    @Test
    void updateHabit_updatesScheduledDays() throws Exception {
        var habit = new Habit("Workout", FIXED);
        habit.setScheduledDays(java.util.EnumSet.of(
                java.time.DayOfWeek.MONDAY,
                java.time.DayOfWeek.WEDNESDAY,
                java.time.DayOfWeek.FRIDAY
        ));
        var saved = repository.save(habit);

        String jsonBody = """
            {
              "name": "Workout",
              "version": 0,
              "scheduledDays": ["TUESDAY", "THURSDAY"]
            }
            """;

        perform(put("/habits/" + saved.getId())
                        .content(jsonBody)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scheduledDays", containsInAnyOrder(
                        "TUESDAY",
                        "THURSDAY"
                )));
    }

    @Test
    void updateHabit_keepsScheduledDays_whenNotProvided() throws Exception {
        var habit = new Habit("Workout", FIXED);
        habit.setScheduledDays(java.util.EnumSet.of(
                java.time.DayOfWeek.MONDAY,
                java.time.DayOfWeek.WEDNESDAY,
                java.time.DayOfWeek.FRIDAY
        ));
        var saved = repository.save(habit);

        String jsonBody = """
            {
              "name": "Updated workout",
              "version": 0
            }
            """;

        perform(put("/habits/" + saved.getId())
                        .content(jsonBody)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated workout"))
                .andExpect(jsonPath("$.scheduledDays", containsInAnyOrder(
                        "MONDAY",
                        "WEDNESDAY",
                        "FRIDAY"
                )));
    }

    @Test
    void completeHabit_returns200_andIncrementsCount() throws Exception {
        var saved = repository.save(new Habit("Read 30 min", FIXED));

        perform(post("/habits/" + saved.getId() + "/complete"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completionCount").value(1));
        perform(get("/habits/" + saved.getId()))
                .andExpect(jsonPath("$.completionCount").value(1));
    }

    @Test
    void completeHabit_returns404_whenNotExists() throws Exception {
        perform(post("/habits/999/complete"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Habit not found: " + 999));
    }

    @Test
    void complete_isIdempotent_whenSameDayTwice() throws Exception {
        var saved = repository.save(new Habit("Read", FIXED));

        perform(post("/habits/" + saved.getId() + "/complete"))
                .andExpect(status().isOk());
        perform(post("/habits/" + saved.getId() + "/complete"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completionCount").value(1));
    }

    @Test
    void completeHabit_startsStreakAtOne_whenFirstCompletion() throws Exception {
        var saved = repository.save(new Habit("Read", FIXED));

        perform(post("/habits/" + saved.getId() + "/complete"));
        perform(get("/habits/" + saved.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStreak").value(1));
    }

    @Test
    void completeHabit_continuesStreak_whenCompletedYesterday() throws Exception {
        var habit = repository.save(new Habit("Read", FIXED));
        habit.complete(
            LocalDate.now(APPLICATION_ZONE).minusDays(1),
            APPLICATION_ZONE
        );
        repository.save(habit);

        perform(post("/habits/" + habit.getId() + "/complete"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStreak").value(2));
    }

    @Test
    void completeHabit_resetsStreakToOne_whenLastCompletedMoreThanOneDayAgo() throws Exception {
        var habit = repository.save(new Habit("Read", FIXED));
        habit.complete(
            LocalDate.now(APPLICATION_ZONE).minusDays(17),
            APPLICATION_ZONE
        );
        repository.save(habit);
        perform(post("/habits/" + habit.getId() + "/complete"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStreak").value(1));
    }

    @Test
    void completingArchivedHabitReturnsBadRequest() throws Exception {
        var saved = repository.save(new Habit("Read", FIXED));

        perform(post("/habits/" + saved.getId() + "/archive"))
                .andExpect(status().isOk());
        perform(post("/habits/" + saved.getId() + "/complete"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Cannot complete: archived"));
    }

    @Test
    void complete_writesOneHistoryRow_whenCompleted() throws Exception {
        var saved = repository.save(new Habit("Read", FIXED));

        perform(post("/habits/" + saved.getId() + "/complete"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completionCount").value(1));
        var rows = completionRepository.findByHabitIdOrderByCompletedOnDesc(saved.getId());
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().getHabitId()).isEqualTo(saved.getId());
        assertThat(rows.getFirst().getCompletedOn()).isEqualTo(LocalDate.now());
    }

    @Test
    void complete_doesNotWriteHistory_whenSameDayTwice() throws Exception {
        var saved = repository.save(new Habit("Read", FIXED));
        perform(post("/habits/" + saved.getId() + "/complete"))
                .andExpect(status().isOk());
        perform(post("/habits/" + saved.getId() + "/complete"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completionCount").value(1));
        var rows = completionRepository.findByHabitIdOrderByCompletedOnDesc(saved.getId());
        assertThat(rows).hasSize(1);
    }

    @Test
    void createHabit_startsWithCompletionCountZero() throws Exception {
        perform(post("/habits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"New habit\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.completionCount").value(0))
                .andExpect(jsonPath("$.scheduledDays").isArray())
                .andExpect(jsonPath("$.scheduledDays.length()").value(7));
    }

    @Test
    void getStats_returns404_whenHabitNotExists() throws Exception {
        perform(get("/habits/999/stats"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Habit not found: " + 999));
    }

    @Test
    void getStats_returnsZeroCountAndNullLastCompleted_forNewHabit() throws Exception {
        var saved = repository.save(new Habit("Read 30 min", FIXED));

        perform(get("/habits/" + saved.getId() + "/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completionCount").value(0))
                .andExpect(jsonPath("$.lastCompletedOn").isEmpty());
    }

    @Test
    void uncomplete_returns404_whenHabitNotExists() throws Exception {
        perform(post("/habits/999/uncomplete"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Habit not found: " + 999));
    }

    @Test
    void uncomplete_returns400_whenCountIsZero() throws Exception {
        var saved = repository.save(new Habit("Read 30 min", FIXED));
        perform(post("/habits/" + saved.getId() + "/uncomplete"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void uncomplete_returns200_andDecrementsCount_andClearsTimestamp() throws Exception {
        var saved = repository.save(new Habit("Read 30 min", FIXED));
        perform(post("/habits/" + saved.getId() + "/complete"));
        perform(post("/habits/" + saved.getId() + "/uncomplete"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completionCount").value(0));
        perform(get("/habits/" + saved.getId() + "/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completionCount").value(0))
                .andExpect(jsonPath("$.lastCompletedOn").isEmpty());
    }

    @Test
    void archivedHabitIsExcludedFromList() throws Exception {
        var habit1 = new Habit("Read", FIXED);
        var habit2 = new Habit("Eat", FIXED);

        repository.save(habit1);
        repository.save(habit2);

        perform(post("/habits/" + habit1.getId() + "/archive"))
                .andExpect(status().isOk());

        perform(get("/habits"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Eat"))
                .andExpect(jsonPath("$.content[0].archived").value(false));
    }

    @Test
    void archivedHabit_exposesArchivedTrue_inListAndGetById() throws Exception {
        var habit = new Habit("Read", FIXED);

        repository.save(habit);

        perform(post("/habits/" + habit.getId() + "/archive"))
                .andExpect(status().isOk());
        perform(get("/habits?includeArchived=true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Read"))
                .andExpect(jsonPath("$.content[0].archived").value(true));

        perform(get("/habits/" + habit.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Read"))
                .andExpect(jsonPath("$.archived").value(true));
    }

    @Test
    void archivedHabitIncludedInList_whenIncludeArchivedTrue() throws Exception {
        var habit1 = new Habit("Read", FIXED);
        var habit2 = new Habit("Eat", FIXED.plusSeconds(1));

        repository.save(habit1);
        repository.save(habit2);

        perform(post("/habits/" + habit1.getId() + "/archive"))
                .andExpect(status().isOk());
        perform(get("/habits?includeArchived=true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].name").value("Eat"))
                .andExpect(jsonPath("$.content[0].archived").value(false))
                .andExpect(jsonPath("$.content[1].name").value("Read"))
                .andExpect(jsonPath("$.content[1].archived").value(true));
        perform(get("/habits?includeArchived=false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Eat"))
                .andExpect(jsonPath("$.content[0].archived").value(false));
    }

    @Test
    void unarchivedHabitReappearsInList() throws Exception {
        var habit = new Habit("Eat", FIXED);
        repository.save(habit);

        perform(post("/habits/" + habit.getId() + "/archive"))
                .andExpect(status().isOk());
        perform(post("/habits/" + habit.getId() + "/unarchive"))
                .andExpect(status().isOk());
        perform(get("/habits"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Eat"))
                .andExpect(jsonPath("$.content[0].archived").value(false));
    }

    @Test
    void getHistory_returns404_whenHabitNotExists() throws Exception {
        perform(get("/habits/999/history"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Habit not found: " + 999));
    }

    @Test
    void getHistory_returnsEmptyList_whenNoCompletions() throws Exception {
        var saved = new Habit("Mess around", FIXED);
        repository.save(saved);

        perform(get("/habits/" + saved.getId() + "/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void getHistory_returnsCompletions_whenCompleted() throws Exception {
        var habit = new Habit("Mess around", FIXED);
        repository.save(habit);

        perform(post("/habits/" + habit.getId() + "/complete"))
                .andExpect(status().isOk());
        perform(get("/habits/" + habit.getId() + "/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").doesNotExist())
                .andExpect(jsonPath("$.content[0].completedOn").value(LocalDate.now().toString()));
    }

    @Test
    void getHistory_returnsEmptyList_whenUncompleted() throws Exception {
        var habit = new Habit("Mess around", FIXED);
        repository.save(habit);

        perform(post("/habits/" + habit.getId() + "/complete"))
                .andExpect(status().isOk());
        perform(post("/habits/" + habit.getId() + "/uncomplete"))
                .andExpect(status().isOk());
        perform(get("/habits/" + habit.getId() + "/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void getHistory_returnsPagedResult_whenMoreThanOnePage() throws Exception {
        var saved = repository.save(new Habit("Read", FIXED));
        Long id = saved.getId();
        completionRepository.save(new HabitCompletion(id, LocalDate.now()));
        completionRepository.save(new HabitCompletion(id, LocalDate.now().minusDays(1)));
        completionRepository.save(new HabitCompletion(id, LocalDate.now().minusDays(2)));

        perform(get("/habits/" + id + "/history?page=0&size=2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void getHistory_filtersCompletionsByDateRange_whenFromAndToProvided() throws Exception {
        var saved = repository.save(new Habit("Read", FIXED));
        Long id = saved.getId();

        completionRepository.save(new HabitCompletion(id, LocalDate.of(2024, 1, 9)));
        completionRepository.save(new HabitCompletion(id, LocalDate.of(2024, 1, 10)));
        completionRepository.save(new HabitCompletion(id, LocalDate.of(2024, 1, 11)));
        completionRepository.save(new HabitCompletion(id, LocalDate.of(2024, 1, 12)));

        perform(get("/habits/" + id + "/history?from=2024-01-10&to=2024-01-11"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].completedOn").value("2024-01-11"))
                .andExpect(jsonPath("$.content[1].completedOn").value("2024-01-10"))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void getHistory_filtersCompletionsFromDate_whenOnlyFromProvided() throws Exception {
        var saved = repository.save(new Habit("Read", FIXED));
        Long id = saved.getId();

        completionRepository.save(new HabitCompletion(id, LocalDate.of(2024, 1, 9)));
        completionRepository.save(new HabitCompletion(id, LocalDate.of(2024, 1, 10)));
        completionRepository.save(new HabitCompletion(id, LocalDate.of(2024, 1, 11)));

        perform(get("/habits/" + id + "/history?from=2024-01-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].completedOn").value("2024-01-11"))
                .andExpect(jsonPath("$.content[1].completedOn").value("2024-01-10"))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void getHistory_filtersCompletionsToDate_whenOnlyToProvided() throws Exception {
        var saved = repository.save(new Habit("Read", FIXED));
        Long id = saved.getId();

        completionRepository.save(new HabitCompletion(id, LocalDate.of(2024, 1, 9)));
        completionRepository.save(new HabitCompletion(id, LocalDate.of(2024, 1, 10)));
        completionRepository.save(new HabitCompletion(id, LocalDate.of(2024, 1, 11)));

        perform(get("/habits/" + id + "/history?to=2024-01-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].completedOn").value("2024-01-10"))
                .andExpect(jsonPath("$.content[1].completedOn").value("2024-01-09"))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void getHistory_returnsBadRequest_whenFromIsAfterTo() throws Exception {
        var saved = repository.save(new Habit("Read", FIXED));
        Long id = saved.getId();

        perform(get("/habits/" + id + "/history")
                        .param("from", "2024-01-12")
                        .param("to", "2024-01-10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error")
                        .value("'from' must not be after 'to'"));
    }

    @Test
    void getHistory_returnsAllCompletionsSortedDesc_whenNoDateRangeProvided() throws Exception {
        var saved = repository.save(new Habit("Read", FIXED));
        Long id = saved.getId();

        completionRepository.save(new HabitCompletion(id, LocalDate.of(2024, 1, 9)));
        completionRepository.save(new HabitCompletion(id, LocalDate.of(2024, 1, 10)));
        completionRepository.save(new HabitCompletion(id, LocalDate.of(2024, 1, 11)));

        perform(get("/habits/" + id + "/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(3))
                .andExpect(jsonPath("$.content[0].completedOn").value("2024-01-11"))
                .andExpect(jsonPath("$.content[1].completedOn").value("2024-01-10"))
                .andExpect(jsonPath("$.content[2].completedOn").value("2024-01-09"))
                .andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    void dueToday_returnsOnlyActiveScheduledAndNotCompletedHabits () throws Exception {
        LocalDate today = LocalDate.now();
        DayOfWeek todayDay = today.getDayOfWeek();
        DayOfWeek otherDay = todayDay.plus(1);

        var due = new Habit("Due", FIXED);
        due.setScheduledDays(EnumSet.of(todayDay));
        repository.save(due);

        var notScheduledToday = new Habit("Not scheduled today", FIXED);
        notScheduledToday.setScheduledDays(EnumSet.of(otherDay));
        repository.save(notScheduledToday);

        var alreadyCompletedToday = new Habit("Already completed today", FIXED);
        alreadyCompletedToday.setScheduledDays(EnumSet.of(todayDay));
        alreadyCompletedToday.complete(today, APPLICATION_ZONE);
        repository.save(alreadyCompletedToday);

        var archivedDue = new Habit("Archive due", FIXED);
        archivedDue.setScheduledDays(EnumSet.of(todayDay));
        repository.save(archivedDue);

        perform(post("/habits/" + archivedDue.getId() + "/archive"))
                .andExpect(status().isOk());
        perform(get("/habits/due-today"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Due"))
                .andExpect(jsonPath("$.content[*].name",
                        not(hasItems("Not scheduled today", "Already completed today", "Archive due"))));
    }

    @Test
    void dueTodayCount_returnsOnlyActiveScheduledAndNotCompletedHabits() throws Exception {
        LocalDate today = LocalDate.now();
        DayOfWeek todayDay = today.getDayOfWeek();
        DayOfWeek otherDay = todayDay.plus(1);

        var dueOne = new Habit("Due one", FIXED);
        dueOne.setScheduledDays(EnumSet.of(todayDay));
        repository.save(dueOne);

        var dueTwo = new Habit("Due two", FIXED);
        dueTwo.setScheduledDays(EnumSet.of(todayDay));
        repository.save(dueTwo);

        var notScheduledToday = new Habit("Not scheduled today", FIXED);
        notScheduledToday.setScheduledDays(EnumSet.of(otherDay));
        repository.save(notScheduledToday);

        var alreadyCompletedToday = new Habit("Already completed today", FIXED);
        alreadyCompletedToday.setScheduledDays(EnumSet.of(todayDay));
        alreadyCompletedToday.complete(today, APPLICATION_ZONE);
        repository.save(alreadyCompletedToday);

        var archivedDue = new Habit("Archived due", FIXED);
        archivedDue.setScheduledDays(EnumSet.of(todayDay));
        repository.save(archivedDue);

        perform(post("/habits/" + archivedDue.getId() + "/archive"))
            .andExpect(status().isOk());
        perform(get("/habits/due-today/count"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.count").value(2));
    }

    @Test
    void dueTodayCount_returnsZero_whenNoDueHabits() throws Exception {
        perform(get("/habits/due-today/count"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.count").value(0));
    }

    @Test
    void dueTodayCount_returnsZero_whenOnlyArchivedHabitsExist() throws Exception {
        LocalDate today = LocalDate.now();
        DayOfWeek todayDay = today.getDayOfWeek();

        var habit = new Habit("Archived", FIXED);
        habit.setScheduledDays(EnumSet.of(todayDay));
        repository.save(habit);

        perform(post("/habits/" + habit.getId() + "/archive"))
            .andExpect(status().isOk());
        perform(get("/habits/due-today/count"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.count").value(0));
    }

    @Test
    void bulkComplete_returnsPerItemResult() throws Exception {
        LocalDate today = LocalDate.now();
        DayOfWeek todayDay = today.getDayOfWeek();
        DayOfWeek otherDay = todayDay.plus(1);

        var completed = new Habit("Completed", FIXED);
        completed.setScheduledDays(EnumSet.of(todayDay));
        repository.save(completed);

        var skipped = new Habit("Skipped", FIXED);
        skipped.setScheduledDays(EnumSet.of(todayDay));
        skipped.complete(today, APPLICATION_ZONE);
        repository.save(skipped);

        var failed = new Habit("Failed", FIXED);
        failed.setScheduledDays(EnumSet.of(otherDay));
        repository.save(failed);

        long missingId = 999999L;

        String jsonBody = """
            {
              "habitIds": [%d, %d, %d, %d]
            }
            """.formatted(
                completed.getId(),
                skipped.getId(),
                failed.getId(),
                missingId
        );

        perform(post("/habits/bulk-complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completed", containsInAnyOrder(
                        completed.getId().intValue()
                )))
                .andExpect(jsonPath("$.skipped", containsInAnyOrder(
                        skipped.getId().intValue()
                )))
                .andExpect(jsonPath("$.failed", containsInAnyOrder(
                        failed.getId().intValue()
                )))
                .andExpect(jsonPath("$.notFound", containsInAnyOrder(
                        (int) missingId
                )))
                .andExpect(jsonPath("$.conflicted.length()").value(0));
    }

    @Test
    void bulkComplete_skipsDuplicateIdAfterFirstCompletion() throws Exception {
        LocalDate today = LocalDate.now();
        DayOfWeek todayDay = today.getDayOfWeek();

        var habit = new Habit("Read", FIXED);
        habit.setScheduledDays(EnumSet.of(todayDay));
        repository.save(habit);

        String jsonBody = """
            {
              "habitIds": [%d, %d]
            }
            """.formatted(habit.getId(), habit.getId());

        perform(post("/habits/bulk-complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completed", containsInAnyOrder(
                        habit.getId().intValue()
                )))
                .andExpect(jsonPath("$.skipped", containsInAnyOrder(
                        habit.getId().intValue()
                )))
                .andExpect(jsonPath("$.failed.length()").value(0))
                .andExpect(jsonPath("$.notFound.length()").value(0))
                .andExpect(jsonPath("$.conflicted.length()").value(0));
    }

    @Test
    void bulkComplete_returns400_whenHabitIdsIsEmpty() throws Exception {
        perform(post("/habits/bulk-complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "habitIds": []
                            }
                            """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void bulkComplete_returns400_whenHabitIdsMissing() throws Exception {
        perform(post("/habits/bulk-complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                            }
                            """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void completionRate_returnsScheduledCompletedAndRoundedRate()
        throws Exception {

        // Creation precedes the measured window in every tested zone;
        // this test covers rate calculation, not zone conversion.
        Habit habit = new Habit(
            "Read",
            Instant.parse("2024-01-01T00:00:00Z")
        );

        LocalDate from = LocalDate.of(2024, 1, 3);
        LocalDate to = from.plusDays(2);

        habit.setScheduledDays(EnumSet.of(
            from.getDayOfWeek(),
            from.plusDays(1).getDayOfWeek(),
            to.getDayOfWeek()
        ));

        Habit saved = repository.save(habit);

        completionStatRepository.save(
            new HabitCompletionStat(
                saved.getId(),
                from,
                1,
                1
            )
        );

        perform(get(
                "/habits/{id}/completion-rate",
                saved.getId()
            )
                .param("from", from.toString())
                .param("to", to.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.scheduled").value(3))
            .andExpect(jsonPath("$.completed").value(1))
            .andExpect(jsonPath("$.rate").value(0.3333));
    }

    @Test
    void completionRate_returns400_whenFromIsMissing() throws Exception {
        perform(get("/habits/{id}/completion-rate", 42L)
                .param("to", "2026-07-31"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void completionRate_returns400_whenFromIsAfterTo() throws Exception {
        perform(get("/habits/{id}/completion-rate", 42L)
                .param("from", "2026-07-31")
                .param("to", "2026-07-01"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void completionRate_returns404_whenHabitDoesNotExist() throws Exception {
        perform(get("/habits/{id}/completion-rate", 999_999L)
                .param("from", "2026-07-01")
                .param("to", "2026-07-31"))
            .andExpect(status().isNotFound());
    }
}
