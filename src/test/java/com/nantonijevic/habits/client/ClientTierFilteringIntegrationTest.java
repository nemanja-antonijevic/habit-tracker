package com.nantonijevic.habits.client;

import com.nantonijevic.habits.AbstractIntegrationTest;
import com.nantonijevic.habits.domain.Habit;
import com.nantonijevic.habits.support.HabitTestFixtureRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
@AutoConfigureMockMvc
class ClientTierFilteringIntegrationTest
    extends AbstractIntegrationTest {

    private static final Instant CREATED_AT =
        Instant.parse("2026-08-19T08:00:00Z");

    private enum PublicEndpoint {
        CREATE,
        LIST,
        GET_BY_ID,
        UPDATE,
        COMPLETE,
        UNCOMPLETE,
        ARCHIVE,
        UNARCHIVE,
        DUE_TODAY
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private HabitTestFixtureRepository habitRepository;

    @Autowired
    private ApiClientRepository apiClientRepository;

    @Autowired
    private ApiKeyHasher apiKeyHasher;

    @Test
    void missingApiKeyUsesPublicTierOnGetById()
        throws Exception {

        Habit saved = habitRepository.save(
            new Habit("Read", CREATED_AT)
        );

        mockMvc.perform(
                get("/habits/{id}", saved.getId())
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(saved.getId()))
            .andExpect(jsonPath("$.name").value("Read"))
            .andExpect(jsonPath("$.completionCount").value(0))
            .andExpect(jsonPath("$.currentStreak").value(0))
            .andExpect(
                jsonPath("$.scheduledDays")
                    .doesNotHaveJsonPath()
            )
            .andExpect(
                jsonPath("$.archived")
                    .doesNotHaveJsonPath()
            )
            .andExpect(
                jsonPath("$.createdAt")
                    .doesNotHaveJsonPath()
            );
    }

    @Test
    void listFiltersFieldsWithoutChangingPaginationMetadata()
        throws Exception {

        apiClientRepository.saveAndFlush(
            new ApiClient(
                apiKeyHasher.hash("internal-key"),
                ClientTier.INTERNAL,
                "Internal client",
                Instant.parse("2026-08-19T09:00:00Z")
            )
        );

        habitRepository.save(
            new Habit("Tier list A", CREATED_AT)
        );

        habitRepository.save(
            new Habit(
                "Tier list B",
                CREATED_AT.plusSeconds(1)
            )
        );

        ResultActions internal = mockMvc.perform(
            get("/habits")
                .param("name", "Tier list")
                .param("size", "10")
                .header(
                    ClientTierArgumentResolver.API_KEY_HEADER,
                    "internal-key"
                )
        );

        internal
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(2))
            .andExpect(jsonPath("$.content.length()").value(2))
            .andExpect(
                jsonPath("$.content[0].scheduledDays")
                    .isArray()
            )
            .andExpect(
                jsonPath("$.content[0].archived")
                    .value(false)
            )
            .andExpect(
                jsonPath("$.content[0].createdAt")
                    .exists()
            )
            .andExpect(
                jsonPath("$.content[1].scheduledDays")
                    .isArray()
            )
            .andExpect(
                jsonPath("$.content[1].archived")
                    .value(false)
            )
            .andExpect(
                jsonPath("$.content[1].createdAt")
                    .exists()
            );

        ResultActions publicResult = mockMvc.perform(
            get("/habits")
                .param("name", "Tier list")
                .param("size", "10")
        );

        publicResult
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(2))
            .andExpect(jsonPath("$.content.length()").value(2))
            .andExpect(
                jsonPath("$.content[0].scheduledDays")
                    .doesNotHaveJsonPath()
            )
            .andExpect(
                jsonPath("$.content[0].archived")
                    .doesNotHaveJsonPath()
            )
            .andExpect(
                jsonPath("$.content[0].createdAt")
                    .doesNotHaveJsonPath()
            )
            .andExpect(
                jsonPath("$.content[1].scheduledDays")
                    .doesNotHaveJsonPath()
            )
            .andExpect(
                jsonPath("$.content[1].archived")
                    .doesNotHaveJsonPath()
            )
            .andExpect(
                jsonPath("$.content[1].createdAt")
                    .doesNotHaveJsonPath()
            );
    }

    @Test
    void completeUsesTrustedTierFiltering()
        throws Exception {

        apiClientRepository.saveAndFlush(
            new ApiClient(
                apiKeyHasher.hash("trusted-key"),
                ClientTier.TRUSTED,
                "Trusted client",
                Instant.parse("2026-08-19T09:00:00Z")
            )
        );

        Habit saved = habitRepository.save(
            new Habit("Complete tier habit", CREATED_AT)
        );

        mockMvc.perform(
                post(
                    "/habits/{id}/complete",
                    saved.getId()
                )
                    .header(
                        ClientTierArgumentResolver.API_KEY_HEADER,
                        "trusted-key"
                    )
            )
            .andExpect(status().isOk())
            .andExpect(
                jsonPath("$.id")
                    .value(saved.getId())
            )
            .andExpect(
                jsonPath("$.completionCount")
                    .value(1)
            )
            .andExpect(
                jsonPath("$.scheduledDays")
                    .isArray()
            )
            .andExpect(
                jsonPath("$.archived")
                    .value(false)
            )
            .andExpect(
                jsonPath("$.createdAt")
                    .doesNotHaveJsonPath()
            );
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(PublicEndpoint.class)
    void everyHabitResponseEndpointAppliesPublicFiltering(
        PublicEndpoint endpoint
    ) throws Exception {
        ResultActions result = performPublicRequest(endpoint);

        String responsePath =
            endpoint == PublicEndpoint.LIST
                || endpoint == PublicEndpoint.DUE_TODAY
                ? "$.content[0]"
                : "$";

        result
            .andExpect(
                endpoint == PublicEndpoint.CREATE
                    ? status().isCreated()
                    : status().isOk()
            )
            .andExpect(
                jsonPath(responsePath + ".id").exists()
            )
            .andExpect(
                jsonPath(responsePath + ".scheduledDays")
                    .doesNotHaveJsonPath()
            )
            .andExpect(
                jsonPath(responsePath + ".archived")
                    .doesNotHaveJsonPath()
            )
            .andExpect(
                jsonPath(responsePath + ".createdAt")
                    .doesNotHaveJsonPath()
            );
    }

    @Test
    void unknownApiKeyIsRejectedAsUnauthorized()
        throws Exception {

        Habit saved = habitRepository.save(
            new Habit(
                "Unknown API key",
                CREATED_AT
            )
        );

        mockMvc.perform(
                get("/habits/{id}", saved.getId())
                    .header(
                        ClientTierArgumentResolver.API_KEY_HEADER,
                        "unknown-key"
                    )
            )
            .andExpect(status().isUnauthorized())
            .andExpect(
                jsonPath("$.error")
                    .value("Invalid API key")
            );
    }

    private ResultActions performPublicRequest(
        PublicEndpoint endpoint
    ) throws Exception {
        String name = "Public tier " + endpoint;

        if (endpoint == PublicEndpoint.CREATE) {
            return mockMvc.perform(
                post("/habits")
                    .contentType(APPLICATION_JSON)
                    .content(
                        """
                        {
                          "name": "%s"
                        }
                        """.formatted(name)
                    )
            );
        }

        Habit habit = new Habit(
            name,
            CREATED_AT.plusSeconds(endpoint.ordinal())
        );

        if (endpoint == PublicEndpoint.UNARCHIVE) {
            habit.archive();
        }

        Habit saved = habitRepository.save(habit);

        if (endpoint == PublicEndpoint.UNCOMPLETE) {
            mockMvc.perform(
                    post(
                        "/habits/{id}/complete",
                        saved.getId()
                    )
                )
                .andExpect(status().isOk());
        }

        MockHttpServletRequestBuilder request = switch (endpoint) {
            case LIST ->
                get("/habits")
                    .param("name", name)
                    .param("size", "10");

            case GET_BY_ID ->
                get("/habits/{id}", saved.getId());

            case UPDATE ->
                put("/habits/{id}", saved.getId())
                    .contentType(APPLICATION_JSON)
                    .content(
                        """
                        {
                          "version": %d,
                          "name": "%s updated"
                        }
                        """.formatted(
                            saved.getVersion(),
                            name
                        )
                    );

            case COMPLETE ->
                post(
                    "/habits/{id}/complete",
                    saved.getId()
                );

            case UNCOMPLETE ->
                post(
                    "/habits/{id}/uncomplete",
                    saved.getId()
                );

            case ARCHIVE ->
                post(
                    "/habits/{id}/archive",
                    saved.getId()
                );

            case UNARCHIVE ->
                post(
                    "/habits/{id}/unarchive",
                    saved.getId()
                );

            case DUE_TODAY ->
                get("/habits/due-today")
                    .param("size", "10");

            case CREATE ->
                throw new IllegalStateException(
                    "CREATE is handled before fixture creation"
                );
        };

        return mockMvc.perform(request);
    }
}
