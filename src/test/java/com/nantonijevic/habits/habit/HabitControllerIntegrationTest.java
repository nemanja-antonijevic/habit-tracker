package com.nantonijevic.habits.habit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Transactional
@SpringBootTest
@AutoConfigureMockMvc
class HabitControllerIntegrationTest {

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
        var saved2 = repository.save(new Habit("Reading"));

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
        String jsonBody = "{\"name\": \"Code 6 hours\"}";

        mockMvc.perform(put("/habits/" + saved.getId()).content(jsonBody).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(saved.getId()))
                .andExpect(jsonPath("$.name").value("Code 6 hours"));
    }

    @Test
    void updateHabit_returns404_whenNotExists() throws Exception {
        String jsonBody = "{\"name\": \"Code 6 hours\"}";

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
}