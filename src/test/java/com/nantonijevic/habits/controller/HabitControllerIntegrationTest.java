package com.nantonijevic.habits.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nantonijevic.habits.domain.Habit;
import com.nantonijevic.habits.dto.CreateHabitRequest;
import com.nantonijevic.habits.AbstractIntegrationTest;
import com.nantonijevic.habits.repository.HabitCompletionRepository;
import com.nantonijevic.habits.repository.HabitRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;

import static org.hamcrest.Matchers.containsString;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Transactional
@AutoConfigureMockMvc
class HabitControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private HabitRepository repository;

    @Autowired
    private HabitCompletionRepository completionRepository;


    @Test
    void createHabit_returns201_andPersistsHabit() throws Exception {
        var request = new CreateHabitRequest("Code 3 hours");

        mockMvc.perform(post("/habits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("Code 3 hours"));
    }

    @Test
    void createHabit_returns400_whenRequestBodyIsEmpty() throws Exception {
        mockMvc.perform(post("/habits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Malformed or missing request body"));
    }

    @Test
    void createHabit_returns400_whenNameIsBlank() throws Exception {
        var request = new CreateHabitRequest("");

        mockMvc.perform(post("/habits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("name must not be blank"));
    }

    @Test
    void createHabit_returns400_whenNameIsTooLong() throws Exception {
        var request = new CreateHabitRequest("a".repeat(256));
        mockMvc.perform(post("/habits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("name size must be between 0 and 255"));
    }

    @Test
    void createHabit_returns400_whenNameIsWhitespaceOnly() throws Exception {
        var request = new CreateHabitRequest("    ");
        mockMvc.perform(post("/habits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("name must not be blank"));
    }

    @Test
    void listHabits_returnsEmptyArray_whenNoHabits() throws Exception {
        mockMvc.perform(get("/habits"))
                .andExpect(status().is(200))
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void listHabits_returnsOneHabit_whenOneExists() throws Exception {
        var request = new CreateHabitRequest("Code 3 hours");

        mockMvc.perform(post("/habits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("Code 3 hours"));

        mockMvc.perform(get("/habits"))
                .andExpect(status().is(200))
                .andExpect(jsonPath("$.content.length()").value(1));

    }

    @Test
    void getHabit_returnsHabit_whenExists() throws Exception {
        var saved = repository.save(new Habit("Code 3 hours"));

        mockMvc.perform(get("/habits/" + saved.getId()))
                .andExpect(status().is(200))
                .andExpect(jsonPath("$.id").value(saved.getId()))
                .andExpect(jsonPath("$.name").value("Code 3 hours"));
    }

    @Test
    void getHabit_returns404_whenNotExists() throws Exception {
        mockMvc.perform(get("/habits/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Habit not found: " + 999999));
    }

    @Test
    void getHabit_returnsCorrectHabit_whenMultipleExists() throws Exception {
        var saved1 = repository.save(new Habit("Sport"));

        mockMvc.perform(get("/habits/" + saved1.getId()))
                .andExpect(status().is(200))
                .andExpect(jsonPath("$.id").value(saved1.getId()))
                .andExpect(jsonPath("$.name").value("Sport"));
    }

    @Test
    void deleteHabit_returns204_andRemovesHabit() throws Exception {
        var saved = repository.save(new Habit("Code 3 hours"));

        mockMvc.perform(delete("/habits/" + saved.getId()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/habits/" + saved.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Habit not found: " + saved.getId()));
    }

    @Test
    void deleteHabit_returns404_whenNotExists() throws Exception {
        mockMvc.perform(delete("/habits/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Habit not found: " + 999999));
    }

    @Test
    void updateHabit_returns200_andUpdatesHabit() throws Exception {
        var saved = repository.save(new Habit("Code 3 hours"));
        String jsonBody = "{\"name\": \"Code 6 hours\",\"version\": 0}";

        mockMvc.perform(put("/habits/" + saved.getId()).content(jsonBody).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(saved.getId()))
                .andExpect(jsonPath("$.name").value("Code 6 hours"));
    }

    @Test
    void updateHabit_returns404_whenNotExists() throws Exception {
        String jsonBody = "{\"name\": \"Code 6 hours\",\"version\": 0}";

        mockMvc.perform(put("/habits/999999").content(jsonBody).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Habit not found: " + 999999));
    }

    @Test
    void updateHabit_returns400_whenBodyIsEmpty() throws Exception {
        mockMvc.perform(put("/habits/999999").content("").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Malformed or missing request body"));
    }

    @Test
    void updateHabit_returns400_whenNameIsBlank_evenIfIdNotFound() throws Exception {
        String jsonBody = "{\"name\": \"\"}";

        mockMvc.perform(put("/habits/999999").content(jsonBody).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("name must not be blank")));
    }


    @Test
    void updateHabit_returns400_whenNameIsTooLong() throws Exception {
        String jsonBody = "{\"name\": \"" + "a".repeat(256) + "\"}";

        mockMvc.perform(put("/habits/999999").content(jsonBody).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error")
                        .value(containsString("name size must be between 0 and 255")));
    }

    @Test
    void updateHabit_returns400_whenNameIsWhitespaceOnly() throws Exception {
        String jsonBody = "{\"name\": \"        \"}";

        mockMvc.perform(put("/habits/999999").content(jsonBody).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("name must not be blank")));
    }

    @Test
    void updateHabit_returns200_whenVersionMatches() throws Exception {
        var saved = repository.save(new Habit("Code 3 hours"));

        String jsonBody = "{\"name\": \"Sleep\",\"version\": 0}";

        mockMvc.perform(put("/habits/" + saved.getId()).content(jsonBody).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        mockMvc.perform(get("/habits/" + saved.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(saved.getId()))
                .andExpect(jsonPath("$.name").value("Sleep"));
    }

    @Test
    void updateHabit_returns409_whenVersionStale() throws Exception {
        var saved = repository.save(new Habit("Code 3 hours"));
        String jsonBody1 = "{\"name\": \"Sleep\",\"version\": 0}";
        String jsonBody2 = "{\"name\": \"Eat\",\"version\": 0}";

        mockMvc.perform(put("/habits/" + saved.getId()).content(jsonBody1).contentType(MediaType.APPLICATION_JSON));
        repository.flush();
        mockMvc.perform(put("/habits/" + saved.getId()).content(jsonBody2).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value(containsString("Habit version conflict")));
    }

    @Test
    void completeHabit_returns200_andIncrementsCount() throws Exception {
        var saved = repository.save(new Habit("Read 30 min"));

        mockMvc.perform(post("/habits/" + saved.getId() + "/complete"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completionCount").value(1));

        mockMvc.perform(get("/habits/" + saved.getId()))
                .andExpect(jsonPath("$.completionCount").value(1));
    }

    @Test
    void completeHabit_returns404_whenNotExists() throws Exception {
        mockMvc.perform(post("/habits/999/complete"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Habit not found: " + 999));
    }

    @Test
    void complete_isIdempotent_whenSameDayTwice() throws Exception {
        var saved = repository.save(new Habit("Read"));

        mockMvc.perform(post("/habits/" + saved.getId() + "/complete"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/habits/" + saved.getId() + "/complete"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completionCount").value(1));
    }

    @Test
    void completeHabit_startsStreakAtOne_whenFirstCompletion() throws Exception {
        var saved = repository.save(new Habit("Read"));
        mockMvc.perform(post("/habits/" + saved.getId() + "/complete"));
        mockMvc.perform(get("/habits/" + saved.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStreak").value(1));
    }

    @Test
    void completeHabit_continuesStreak_whenCompletedYesterday() throws Exception {
        var habit = repository.save(new Habit("Read"));
        habit.complete(LocalDate.now().minusDays(1));
        repository.save(habit);
        mockMvc.perform(post("/habits/" + habit.getId() + "/complete"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStreak").value(2));
    }

    @Test
    void completeHabit_resetsStreakToOne_whenLastCompletedMoreThanOneDayAgo() throws Exception {
        var habit = repository.save(new Habit("Read"));
        habit.complete(LocalDate.now().minusDays(17));
        repository.save(habit);
        mockMvc.perform(post("/habits/" + habit.getId() + "/complete"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStreak").value(1));
    }

    @Test
    void completingArchivedHabitReturnsBadRequest() throws Exception {
        var saved = repository.save(new Habit("Read"));

        mockMvc.perform(post("/habits/" + saved.getId() + "/archive"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/habits/" + saved.getId() + "/complete"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Cannot complete: archived"));
    }

    @Test
    void complete_writesOneHistoryRow_whenCompleted() throws Exception {
        var saved = repository.save(new Habit("Read"));

        mockMvc.perform(post("/habits/" + saved.getId() + "/complete"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completionCount").value(1));
        var rows = completionRepository.findByHabitIdOrderByCompletedOnDesc(saved.getId());
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getHabitId()).isEqualTo(saved.getId());
        assertThat(rows.get(0).getCompletedOn()).isEqualTo(LocalDate.now());
    }

    @Test
    void complete_doesNotWriteHistory_whenSameDayTwice() throws Exception {
        var saved = repository.save(new Habit("Read"));
        mockMvc.perform(post("/habits/" + saved.getId() + "/complete"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/habits/" + saved.getId() + "/complete"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completionCount").value(1));
        var rows = completionRepository.findByHabitIdOrderByCompletedOnDesc(saved.getId());
        assertThat(rows).hasSize(1);
    }

    @Test
    void createHabit_startsWithCompletionCountZero() throws Exception {
        mockMvc.perform(post("/habits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"New habit\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.completionCount").value(0));
    }

    @Test
    void getStats_returns404_whenHabitNotExists() throws Exception {
        mockMvc.perform(get("/habits/999/stats"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Habit not found: " + 999));
    }

    @Test
    void getStats_returnsZeroCountAndNullLastCompleted_forNewHabit() throws Exception {
        var saved = repository.save(new Habit("Read 30 min"));

        mockMvc.perform(get("/habits/" + saved.getId() + "/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completionCount").value(0))
                .andExpect(jsonPath("$.lastCompletedOn").isEmpty());
    }

    @Test
    void uncomplete_returns404_whenHabitNotExists() throws Exception {
        mockMvc.perform(post("/habits/999/uncomplete"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Habit not found: " + 999));
    }

    @Test
    void uncomplete_returns400_whenCountIsZero() throws Exception {
        var saved = repository.save(new Habit("Read 30 min"));
        mockMvc.perform(post("/habits/" + saved.getId() + "/uncomplete"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void uncomplete_returns200_andDecrementsCount_andClearsTimestamp() throws Exception {
        var saved = repository.save(new Habit("Read 30 min"));
        mockMvc.perform(post("/habits/" + saved.getId() + "/complete"));
        mockMvc.perform(post("/habits/" + saved.getId() + "/uncomplete"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completionCount").value(0));
        mockMvc.perform(get("/habits/" + saved.getId() + "/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completionCount").value(0))
                .andExpect(jsonPath("$.lastCompletedOn").isEmpty());
    }

    @Test
    void archivedHabitIsExcludedFromList() throws Exception {
        var habit1 = new Habit("Read");
        var habit2 = new Habit("Eat");

        repository.save(habit1);
        repository.save(habit2);

        mockMvc.perform(post("/habits/" + habit1.getId() + "/archive"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/habits"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Eat"));
    }

    @Test
    void unarchivedHabitReappearsInList() throws Exception {
        var habit = new Habit("Eat");
        repository.save(habit);

        mockMvc.perform(post("/habits/" + habit.getId() + "/archive"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/habits/" + habit.getId() + "/unarchive"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/habits"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Eat"));
    }

    @Test
    void getHistory_returns404_whenHabitNotExists() throws Exception {
        mockMvc.perform(get("/habits/999/history"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Habit not found: " + 999));
    }

    @Test
    void getHistory_returnsEmptyList_whenNoCompletions() throws Exception {
        var saved = new Habit("Mess around");
        repository.save(saved);

        mockMvc.perform(get("/habits/" + saved.getId() + "/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void getHistory_returnsCompletions_whenCompleted() throws Exception {
        var habit = new Habit("Mess around");
        repository.save(habit);

        mockMvc.perform(post("/habits/" + habit.getId() + "/complete"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/habits/" + habit.getId() + "/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].completedOn").value(LocalDate.now().toString()));
    }
}
