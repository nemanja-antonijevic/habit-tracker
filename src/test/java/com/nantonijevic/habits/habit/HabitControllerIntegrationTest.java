package com.nantonijevic.habits.habit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Transactional
@AutoConfigureMockMvc
class HabitControllerIntegrationTest extends AbstractIntegrationTest{

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private HabitRepository repository;


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
    void createHabit_returns400_whenNameIsBlank() throws Exception {
        var request = new CreateHabitRequest("");

        mockMvc.perform(post("/habits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createHabit_returns400_whenNameIsTooLong() throws Exception {
        var request = new CreateHabitRequest("a".repeat(256));
        mockMvc.perform(post("/habits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createHabit_returns400_whenNameIsWhitespaceOnly() throws Exception {
        var request = new CreateHabitRequest("    ");
        mockMvc.perform(post("/habits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listHabits_returnsEmptyArray_whenNoHabits() throws Exception{
        mockMvc.perform(get("/habits"))
                .andExpect(status().is(200))
                .andExpect(jsonPath("$.length()").value(0));
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
                .andExpect(jsonPath("$.length()").value(1));

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
                .andExpect(status().isNotFound());
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
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteHabit_returns404_whenNotExists() throws Exception {
        mockMvc.perform(delete("/habits/999999"))
                .andExpect(status().isNotFound());
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
                .andExpect(status().isNotFound());
    }

    @Test
    void updateHabit_returns400_whenNameIsBlank_evenIfIdNotFound() throws Exception {
        String jsonBody = "{\"name\": \"\"}";

        mockMvc.perform(put("/habits/999999").content(jsonBody).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }


    @Test
    void updateHabit_returns400_whenNameIsTooLong() throws Exception {
        String jsonBody = "{\"name\": \""+ "a".repeat(256) +"\"}";

        mockMvc.perform(put("/habits/999999").content(jsonBody).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateHabit_returns400_whenNameIsWhitespaceOnly() throws Exception {
        String jsonBody = "{\"name\": \"        \"}";

        mockMvc.perform(put("/habits/999999").content(jsonBody).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
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
                .andExpect(status().isConflict());
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
                .andExpect(status().isNotFound());
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
    void completingArchivedHabitReturnsConflict() throws Exception {
        var saved = repository.save(new Habit("Read"));

        mockMvc.perform(post( "/habits/" + saved.getId() + "/archive"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/habits/" + saved.getId() + "/complete"))
                .andExpect(status().isConflict());
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
                .andExpect(status().isNotFound());
    }

    @Test
    void getStats_returnsZeroCountAndNullLastCompleted_forNewHabit() throws Exception {
        var saved = repository.save(new Habit("Read 30 min"));

        mockMvc.perform(get("/habits/" + saved.getId() + "/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completionCount").value(0))
                .andExpect(jsonPath("$.lastCompletedAt").isEmpty());
    }

    @Test
    void getStats_returnsCorrectCountAndTimestamp_afterComplete() throws Exception {
        var saved = repository.save(new Habit("Read 30 min"));
        mockMvc.perform(post("/habits/" + saved.getId() + "/complete"));

        mockMvc.perform(get("/habits/" + saved.getId() + "/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completionCount").value(1))
                .andExpect(jsonPath("$.lastCompletedAt").isNotEmpty());
    }

    @Test
    void getStats_returnsCurrentStreak_afterConsecutiveCompletions() throws Exception {
        var habit = repository.save(new Habit("Read"));
        habit.complete(LocalDate.now().minusDays(2));
        habit.complete(LocalDate.now().minusDays(1));
        repository.save(habit);
        mockMvc.perform(post("/habits/" + habit.getId() + "/complete"));
        mockMvc.perform(get("/habits/" + habit.getId() + "/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStreak").value(3));
    }

    @Test
    void uncomplete_returns404_whenHabitNotExists() throws Exception {
        mockMvc.perform(post("/habits/999/uncomplete"))
                .andExpect(status().isNotFound());
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
                .andExpect(jsonPath("$.lastCompletedAt").isEmpty());
    }

    @Test
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
        var habit = new Habit("Read 30 min");
        repository.save(habit);
        mockMvc.perform(post("/habits/" + habit.getId() + "/complete"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/habits/" + habit.getId()  + "/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStreak").value(1))
                .andExpect(jsonPath("$.longestStreak").value(1));
    }

    @Test
    void streakResetDoesNotLowerLongestStreak() throws Exception {
        var habit = new Habit("Read 30 min");
        habit.complete(LocalDate.now().minusDays(4));
        habit.complete(LocalDate.now().minusDays(3));
        habit.complete(LocalDate.now().minusDays(2));
        repository.save(habit);
        mockMvc.perform(post("/habits/" + habit.getId() + "/complete"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/habits/" + habit.getId() + "/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStreak").value(1))
                .andExpect(jsonPath("$.longestStreak").value(3));
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
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Eat"));
    }

    @Test
    void unarchivedHabitReappearsInList() throws Exception {
        var habit1 = new Habit("Eat");
        repository.save(habit1);

        mockMvc.perform(post("/habits/" + habit1.getId() + "/archive"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/habits/" + habit1.getId() + "/unarchive"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/habits"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Eat"));
    }
}