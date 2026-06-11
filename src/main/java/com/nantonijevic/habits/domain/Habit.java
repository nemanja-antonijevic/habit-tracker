package com.nantonijevic.habits.domain;

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

    @Column(nullable = false)
    private boolean archived;

    @Column(name = "longest_streak")
    private int longestStreak;

    @Column(name = "current_streak")
    private int currentStreak;

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

    public boolean isArchived() { return  archived; }

    public int getLongestStreak() {
        return longestStreak;
    }

    public int getCurrentStreak() {
        return currentStreak;
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

    public boolean complete(LocalDate today) {
        ZoneId zone = ZoneId.systemDefault();

        if(this.archived) throw new IllegalStateException("Cannot complete: archived");

        if (lastCompletedAt != null) {
            LocalDate lastDate = LocalDate.ofInstant(lastCompletedAt, zone);

            if (lastDate.isEqual(today)) {
                return false;
            }

            if (lastDate.isEqual(today.minusDays(1))) {
                currentStreak++;
            } else {
                currentStreak = 1;
            }
        } else {
            currentStreak = 1;
        }

        if (currentStreak > longestStreak) longestStreak = currentStreak;

        lastCompletedAt = today.atStartOfDay(zone).toInstant();
        completionCount++;
        return true;
    }

    public void archive(){
        this.archived = true;
    }

    public void unarchive(){
        this.archived = false;
    }
}
