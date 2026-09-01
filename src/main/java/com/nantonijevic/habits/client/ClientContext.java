package com.nantonijevic.habits.client;

public record ClientContext(
    long clientId,
    ClientTier tier
) {
}
