package com.nantonijevic.habits.client;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
public class ApiKeyHasher {

    private static final String ALGORITHM = "SHA-256";

    public String hash(String apiKey) {
        try {
            MessageDigest digest =
                MessageDigest.getInstance(ALGORITHM);

            byte[] hash = digest.digest(
                apiKey.getBytes(StandardCharsets.UTF_8)
            );

            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                "SHA-256 is not available",
                exception
            );
        }
    }
}
