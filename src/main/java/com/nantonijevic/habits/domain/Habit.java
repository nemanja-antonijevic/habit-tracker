package com.nantonijevic.habits.domain;

import jakarta.persistence.*;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumSet;

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

    @Column(name = "scheduled_days", nullable = false)
    @Convert(converter = DayOfWeekSetConverter.class)
    private EnumSet<DayOfWeek> scheduledDays = EnumSet.allOf(DayOfWeek.class);

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

    public EnumSet<DayOfWeek> getScheduledDays() {
        return EnumSet.copyOf(scheduledDays);
    }

    public void setScheduledDays(EnumSet<DayOfWeek> scheduledDays) {
        requireNonEmptySchedule(scheduledDays);
        this.scheduledDays = EnumSet.copyOf(scheduledDays);
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

    // Known limitation: currentStreak is maintained arithmetically here (decremented by one),
    // not reconstructed from completion history. When the completion being undone had reset the
    // streak (a gap preceded it), this decrement yields a value that does not reflect the streak
    // as of previousCompletionDate. This is pre-existing behaviour, not introduced by the
    // scheduling feature. Fixing it requires recomputing the streak from history + schedule,
    // tracked as a separate task.
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
        if (!isScheduledFor(today)) {
            throw new InvalidHabitStateException("Habit is not scheduled for today.");
        }

        ZoneId zone = ZoneId.systemDefault();

        if (this.archived) throw new InvalidHabitStateException("Cannot complete: archived");

        if (lastCompletedAt != null) {
            LocalDate lastDate = LocalDate.ofInstant(lastCompletedAt, zone);

            if (lastDate.isEqual(today)) {
                return false;
            }

            if (lastDate.isEqual(previousScheduledDateBefore(today))) {
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

        if (lastCompletedDate.isEqual(today) || lastCompletedDate.isEqual(previousScheduledDateBefore(today))) {
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

    public boolean isScheduledFor(LocalDate date) {
        return scheduledDays.contains(date.getDayOfWeek());
    }

    public LocalDate previousScheduledDateBefore(LocalDate date) {
        LocalDate candidate = date.minusDays(1);

        while (!isScheduledFor(candidate)) {
            candidate = candidate.minusDays(1);
        }

        return candidate;
    }

    private void requireNonEmptySchedule(EnumSet<DayOfWeek> scheduledDays) {
        if (scheduledDays == null || scheduledDays.isEmpty()) {
            throw new IllegalArgumentException("Habit schedule must contain at least one day.");
        }
    }
}
