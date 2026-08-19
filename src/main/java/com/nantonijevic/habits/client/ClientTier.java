package com.nantonijevic.habits.client;

public enum ClientTier {

    INTERNAL(true, true, true),
    TRUSTED(true, true, false),
    PUBLIC(false, false, false);

    private final boolean exposesScheduledDays;

    private final boolean exposesArchived;

    private final boolean exposesCreatedAt;

    ClientTier(
        boolean exposesScheduledDays,
        boolean exposesArchived,
        boolean exposesCreatedAt
    ) {
        this.exposesScheduledDays = exposesScheduledDays;
        this.exposesArchived = exposesArchived;
        this.exposesCreatedAt = exposesCreatedAt;
    }

    public boolean exposesScheduledDays() {
        return exposesScheduledDays;
    }

    public boolean exposesArchived() {
        return exposesArchived;
    }

    public boolean exposesCreatedAt() {
        return exposesCreatedAt;
    }
}
