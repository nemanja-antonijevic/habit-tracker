package com.nantonijevic.habits.client;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiKeyHasherTest {

    private final ApiKeyHasher hasher =
        new ApiKeyHasher();

    @Test
    void hashesApiKeyAsLowercaseSha256Hex() {
        assertThat(
            hasher.hash("local-internal-key")
        ).isEqualTo(
            "60a2286a5007c8e4c2664246e14f73936"
                + "f55b0b96b4652933d90e21b2fa068b8"
        );
    }
}
