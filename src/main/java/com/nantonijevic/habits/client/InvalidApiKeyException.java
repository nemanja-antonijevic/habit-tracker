package com.nantonijevic.habits.client;

public class InvalidApiKeyException
    extends RuntimeException {

    public InvalidApiKeyException() {
        super("Invalid API key");
    }
}
