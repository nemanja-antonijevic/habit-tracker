package com.nantonijevic.habits.domain;

import jakarta.persistence.*;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;

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

    // Undoes the completion made today (the only one this can undo, per the guards below) and
    // recomputes both currentStreak and longestStreak from the remaining completion history,
    // walking consecutive scheduled days so a gap correctly breaks a run. currentStreak is gated
    // to the live window (latest remaining completion must be today or the previous scheduled day),
    // so an undo that leaves a gap before today yields a current streak of zero while longestStreak
    // still reflects the best past run.
    //
    // Scope: the complete() path remains arithmetic (it is correct there); only this undo path
    // reconstructs from history. A full derived-streak refactor (dropping the stored columns) is
    // out of scope. The Kafka read-side projection is untouched: it heals itself when the latest
    // HabitCompletionStat snapshot is deleted on the uncompleted event.
    public void decrementCompletionCount(LocalDate today, List<LocalDate> remainingCompletionDates) {
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

        if (remainingCompletionDates.isEmpty()) {
            this.lastCompletedAt = null;
            this.currentStreak = 0;
            this.longestStreak = 0;
            return;
        }

        List<LocalDate> completedDatesAsc = remainingCompletionDates.stream()
                .sorted()
                .toList();

        int longest = 0;
        int currentRun = 0;
        LocalDate previousDate = null;

        for (LocalDate completedDate : completedDatesAsc) {
            if (previousDate != null
                    && previousScheduledDateBefore(completedDate).isEqual(previousDate)) {
                currentRun++;
            } else {
                currentRun = 1;
            }

            longest = Math.max(longest, currentRun);
            previousDate = completedDate;
        }

        LocalDate latestCompletedDate = completedDatesAsc.getLast();

        boolean currentStreakIsAlive = latestCompletedDate.isEqual(today)
                || latestCompletedDate.isEqual(previousScheduledDateBefore(today));

        this.lastCompletedAt = latestCompletedDate.atStartOfDay(zone).toInstant();
        this.currentStreak = currentStreakIsAlive ? currentRun : 0;
        this.longestStreak = longest;
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

    public boolean wasCompletedOn(LocalDate date) {
        if (lastCompletedAt == null) {
            return false;
        }

        return LocalDate.ofInstant(lastCompletedAt, ZoneId.systemDefault())
                .isEqual(date);
    }
}
