package com.nantonijevic.habits.habit;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "habits")
public class Habit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

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

    public void incrementCompletionCount() {
        this.completionCount++;
        this.lastCompletedAt = Instant.now();
    }
}
