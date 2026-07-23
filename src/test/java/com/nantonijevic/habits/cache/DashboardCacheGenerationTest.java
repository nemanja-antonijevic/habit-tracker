package com.nantonijevic.habits.cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardCacheGenerationTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private DashboardCacheGeneration generation;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue())
            .thenReturn(valueOperations);

        generation =
            new DashboardCacheGeneration(redisTemplate);
    }

    @Test
    void currentReturnsZeroWhenGenerationDoesNotExist() {
        when(valueOperations.get(
            DashboardCacheGeneration.GENERATION_KEY
        )).thenReturn(null);

        long current = generation.current();

        assertThat(current).isZero();
    }

    @Test
    void currentReturnsStoredGeneration() {
        when(valueOperations.get(
            DashboardCacheGeneration.GENERATION_KEY
        )).thenReturn("7");

        long current = generation.current();

        assertThat(current).isEqualTo(7L);
    }

    @Test
    void advanceReturnsAtomicallyIncrementedGeneration() {
        when(valueOperations.increment(
            DashboardCacheGeneration.GENERATION_KEY
        )).thenReturn(8L);

        long advanced = generation.advance();

        assertThat(advanced).isEqualTo(8L);
    }

    @Test
    void advanceFailsWhenRedisDoesNotReturnGeneration() {
        when(valueOperations.increment(
            DashboardCacheGeneration.GENERATION_KEY
        )).thenReturn(null);

        assertThatThrownBy(() -> generation.advance())
            .isInstanceOf(IllegalStateException.class)
            .hasMessage(
                "Redis did not return the advanced "
                    + "dashboard cache generation"
            );
    }
}
