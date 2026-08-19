package com.nantonijevic.habits.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nantonijevic.habits.dto.HabitResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.DayOfWeek;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class HabitResponseTransformerTest {

    private static final Set<DayOfWeek> SCHEDULED_DAYS =
        Set.of(
            DayOfWeek.MONDAY,
            DayOfWeek.WEDNESDAY
        );

    private static final Instant CREATED_AT =
        Instant.parse("2026-08-19T08:00:00Z");

    private final HabitResponseTransformer transformer =
        new HabitResponseTransformer();

    private final ObjectMapper objectMapper =
        new ObjectMapper().findAndRegisterModules();

    @ParameterizedTest
    @MethodSource("tierExpectations")
    void transformsExactlyTheFieldsAllowedForTier(
        ClientTier tier,
        HabitResponse expected
    ) {
        HabitResponse source = fullResponse();

        HabitResponse transformed =
            transformer.transform(source, tier);

        assertThat(transformed).isEqualTo(expected);

        assertThat(source)
            .as("immutable source must remain unchanged")
            .isEqualTo(fullResponse());
    }

    @Test
    void pageTransformationPreservesPaginationMetadata() {
        Page<HabitResponse> source = new PageImpl<>(
            List.of(fullResponse()),
            PageRequest.of(2, 5),
            13
        );

        Page<HabitResponse> transformed =
            transformer.transform(
                source,
                ClientTier.PUBLIC
            );

        assertThat(transformed.getContent())
            .containsExactly(publicResponse());

        assertThat(transformed.getNumber())
            .isEqualTo(source.getNumber());

        assertThat(transformed.getSize())
            .isEqualTo(source.getSize());

        assertThat(transformed.getTotalElements())
            .isEqualTo(source.getTotalElements());

        assertThat(transformed.getTotalPages())
            .isEqualTo(source.getTotalPages());
    }

    @Test
    void internalTransformationKeepsEmptyScheduledDaysInJson() {
        HabitResponse source = response(
            Set.of(),
            false,
            CREATED_AT
        );

        HabitResponse transformed = transformer.transform(
            source,
            ClientTier.INTERNAL
        );

        JsonNode json = objectMapper.valueToTree(transformed);

        assertThat(transformed.scheduledDays()).isEmpty();
        assertThat(json.has("scheduledDays")).isTrue();
        assertThat(json.get("scheduledDays").isArray()).isTrue();
        assertThat(json.get("scheduledDays").size()).isZero();
    }

    private static Stream<Arguments> tierExpectations() {
        return Stream.of(
            Arguments.of(
                ClientTier.INTERNAL,
                fullResponse()
            ),
            Arguments.of(
                ClientTier.TRUSTED,
                trustedResponse()
            ),
            Arguments.of(
                ClientTier.PUBLIC,
                publicResponse()
            )
        );
    }

    private static HabitResponse fullResponse() {
        return response(
            SCHEDULED_DAYS,
            true,
            CREATED_AT
        );
    }

    private static HabitResponse trustedResponse() {
        return response(
            SCHEDULED_DAYS,
            true,
            null
        );
    }

    private static HabitResponse publicResponse() {
        return response(
            null,
            null,
            null
        );
    }

    private static HabitResponse response(
        Set<DayOfWeek> scheduledDays,
        Boolean archived,
        Instant createdAt
    ) {
        return new HabitResponse(
            42L,
            "Read",
            scheduledDays,
            7,
            3,
            archived,
            createdAt
        );
    }
}
