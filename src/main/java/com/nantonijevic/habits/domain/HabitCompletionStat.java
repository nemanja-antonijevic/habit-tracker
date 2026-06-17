package com.nantonijevic.habits.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "habit_completion_stats")
public class HabitCompletionStat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "habit_id", nullable = false)
    private Long habitId;

    @Column(name = "completed_on", nullable = false)
    private LocalDate completedOn;

    @Column(name = "current_streak", nullable = false)
    private int currentStreak;

    @Column(name = "completion_count", nullable = false)
    private int completionCount;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    protected HabitCompletionStat() {
    }

    public HabitCompletionStat(Long habitId, LocalDate completedOn, int currentStreak, int completionCount) {
        this.habitId = habitId;
        this.completedOn = completedOn;
        this.currentStreak = currentStreak;
        this.completionCount = completionCount;
        this.recordedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getHabitId() {
        return habitId;
    }

    public LocalDate getCompletedOn() {
        return completedOn;
    }

    public int getCurrentStreak() {
        return currentStreak;
    }

    public int getCompletionCount() {
        return completionCount;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }
}
