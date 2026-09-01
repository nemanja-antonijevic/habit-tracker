package com.nantonijevic.habits.client;

import com.nantonijevic.habits.AbstractIntegrationTest;
import com.nantonijevic.habits.support.InternalApiClientFixture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(InternalApiClientFixture.class)
@Transactional
@AutoConfigureMockMvc
class HabitAuthenticationIntegrationTest
    extends AbstractIntegrationTest {

    private enum EndpointWithoutTierParameter {
        BULK_COMPLETE,
        DELETE,
        HABIT_STATS,
        HISTORY,
        COMPLETION_RATE,
        DUE_TODAY_COUNT,
        DASHBOARD_STATS
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InternalApiClientFixture apiClientFixture;

    @ParameterizedTest(name = "{0}")
    @EnumSource(EndpointWithoutTierParameter.class)
    void endpointWithoutTierParameterRejectsMissingApiKey(
        EndpointWithoutTierParameter endpoint
    ) throws Exception {
        mockMvc.perform(requestFor(endpoint))
            .andExpect(status().isUnauthorized())
            .andExpect(
                jsonPath("$.error")
                    .value("Invalid API key")
            );
    }

    @Test
    void unknownApiKeyIsRejectedBeforeBodyValidation()
        throws Exception {
        mockMvc.perform(
                post("/habits")
                    .header(
                        ClientTierArgumentResolver.API_KEY_HEADER,
                        "unknown-key"
                    )
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "name": ""
                        }
                        """
                    )
            )
            .andExpect(status().isUnauthorized())
            .andExpect(
                jsonPath("$.error")
                    .value("Invalid API key")
            );
    }

    @Test
    void revokedApiKeyIsRejected()
        throws Exception {
        String revokedApiKey =
            apiClientFixture
                .provisionRevokedInternalClient();

        mockMvc.perform(
                get("/habits")
                    .header(
                        ClientTierArgumentResolver.API_KEY_HEADER,
                        revokedApiKey
                    )
            )
            .andExpect(status().isUnauthorized())
            .andExpect(
                jsonPath("$.error")
                    .value("Invalid API key")
            );
    }

    private MockHttpServletRequestBuilder requestFor(
        EndpointWithoutTierParameter endpoint
    ) {
        return switch (endpoint) {
            case BULK_COMPLETE ->
                post("/habits/bulk-complete")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "habitIds": [1]
                        }
                        """
                    );
            case DELETE ->
                delete("/habits/{id}", 1L);
            case HABIT_STATS ->
                get("/habits/{id}/stats", 1L);
            case HISTORY ->
                get("/habits/{id}/history", 1L);
            case COMPLETION_RATE ->
                get(
                    "/habits/{id}/completion-rate",
                    1L
                )
                    .param("from", "2026-08-01")
                    .param("to", "2026-08-31");
            case DUE_TODAY_COUNT ->
                get("/habits/due-today/count");
            case DASHBOARD_STATS ->
                get("/habits/stats");
        };
    }
}
