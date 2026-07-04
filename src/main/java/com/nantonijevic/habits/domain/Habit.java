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

    public boolean isArchived() {
        return archived;
    }

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

    public void decrementCompletionCount(LocalDate today, LocalDate previousCompletionDate) {
        ZoneId zone = ZoneId.systemDefault();

        if (this.archived) {
            throw new InvalidHabitStateException("Cannot uncomplete: archived");
        }

        if (this.completionCount == 0 || this.lastCompletedAt == null) {
            throw new InvalidHabitStateException("Cannot uncomplete: count is already zero");
        }

        LocalDate lastDate = LocalDate.ofInstant(lastCompletedAt, zone);

        if (!lastDate.isEqual(today)) {
            throw new InvalidHabitStateException("Cannot uncomplete: habit was not completed today");
        }

        this.completionCount--;

        if (previousCompletionDate == null) {
            this.lastCompletedAt = null;
            this.currentStreak = 0;
        } else {
            this.lastCompletedAt = previousCompletionDate.atStartOfDay(zone).toInstant();
        }

        if (this.currentStreak > 0) {
            this.currentStreak--;
        }
    }

    public boolean complete(LocalDate today) {
        ZoneId zone = ZoneId.systemDefault();

        if (this.archived) throw new InvalidHabitStateException("Cannot complete: archived");

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

    public int effectiveCurrentStreak(LocalDate today) {
        if (lastCompletedAt == null) {
            return 0;
        }

        LocalDate lastCompletedDate = lastCompletedAt
                .atZone(ZoneId.systemDefault())
                .toLocalDate();

        if (lastCompletedDate.isEqual(today) || lastCompletedDate.isEqual(today.minusDays(1))) {
            return currentStreak;
        }

        return 0;
    }

    public void archive() {
        this.archived = true;
    }

    public void unarchive() {
        this.archived = false;
    }
}
