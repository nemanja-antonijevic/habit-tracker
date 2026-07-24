package com.nantonijevic.habits.cache;

import com.nantonijevic.habits.service.HabitService;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;

import java.lang.reflect.Method;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DashboardCacheKeyGeneratorTest {

    private final DashboardCacheGeneration generation =
        mock(DashboardCacheGeneration.class);

    private final DashboardCacheKeyGenerator keyGenerator =
        new DashboardCacheKeyGenerator(generation);

    @Test
    void generatesVersionedDashboardCacheKey() throws Exception {
        LocalDate today = LocalDate.of(2026, 7, 24);

        Method method = HabitService.class.getMethod(
            "getDashboardStats",
            LocalDate.class
        );

        when(generation.current()).thenReturn(7L);

        Object key = keyGenerator.generate(
            new Object(),
            method,
            today
        );

        assertThat(key).isEqualTo(
            "7::2026-07-24"
        );
    }

    @Test
    void generatesUniqueBypassKeyWhenGenerationReadFails()
        throws Exception {

        LocalDate today = LocalDate.of(2026, 7, 24);

        Method method = HabitService.class.getMethod(
            "getDashboardStats",
            LocalDate.class
        );

        when(generation.current()).thenThrow(
            new RedisConnectionFailureException(
                "Redis unavailable"
            )
        );

        Object firstKey = keyGenerator.generate(
            new Object(),
            method,
            today
        );

        Object secondKey = keyGenerator.generate(
            new Object(),
            method,
            today
        );

        assertThat(firstKey.toString())
            .startsWith("bypass::")
            .endsWith("::2026-07-24");

        assertThat(secondKey.toString())
            .startsWith("bypass::")
            .endsWith("::2026-07-24");

        assertThat(firstKey).isNotEqualTo(secondKey);
    }

    @Test
    void structuralGenerationFailureStillEscapes()
        throws Exception {

        LocalDate today = LocalDate.of(2026, 7, 24);

        Method method = HabitService.class.getMethod(
            "getDashboardStats",
            LocalDate.class
        );

        when(generation.current()).thenThrow(
            new IllegalStateException(
                "Invalid generation state"
            )
        );

        assertThatThrownBy(
            () -> keyGenerator.generate(
                new Object(),
                method,
                today
            )
        )
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Invalid generation state");
    }
}
