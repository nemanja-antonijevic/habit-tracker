package com.nantonijevic.habits.habit;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

@Entity
@Table(name = "habits")
public class Habit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @Column(nullable = false)
    private String name;

    @Column(name = "completion_count", nullable = false)
    private int completionCount;

    @Column(name = "last_completed_at")
    private Instant lastCompletedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Habit() {
    }

    public Habit(String name) {
        this.name = name;
        this.createdAt = Instant.now();
    }

    public Long getVersion() {
        return version;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getCompletionCount() {
        return completionCount;
    }

    public Instant getLastCompletedAt() {
        return lastCompletedAt;
    }

    public void decrementCompletionCount() {
        if (this.completionCount == 0) {
            throw new IllegalStateException("Cannot uncomplete: count is already zero");
        }
        this.completionCount--;
        this.lastCompletedAt = null;
    }

    public void complete(LocalDate today) {
        ZoneId zone = ZoneId.systemDefault();
        if (lastCompletedAt != null
                && LocalDate.ofInstant(lastCompletedAt, zone).equals(today)) {
            return;
        }
        lastCompletedAt = today.atStartOfDay(zone).toInstant();
        completionCount++;
    }
}
