package com.reloop.repair.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Objects;

/**
 * Opening hours of a workshop for one day of the week (local time, Asia/Kolkata).
 * Days without an entry are closed. Overnight ranges are not supported: {@code opensAt < closesAt}.
 */
@Embeddable
public class WorkingHours {

    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false, length = 10)
    private DayOfWeek dayOfWeek;

    @Column(name = "opens_at", nullable = false)
    private LocalTime opensAt;

    @Column(name = "closes_at", nullable = false)
    private LocalTime closesAt;

    protected WorkingHours() {
    }

    public WorkingHours(DayOfWeek dayOfWeek, LocalTime opensAt, LocalTime closesAt) {
        this.dayOfWeek = dayOfWeek;
        this.opensAt = opensAt;
        this.closesAt = closesAt;
    }

    public DayOfWeek getDayOfWeek() {
        return dayOfWeek;
    }

    public LocalTime getOpensAt() {
        return opensAt;
    }

    public LocalTime getClosesAt() {
        return closesAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof WorkingHours other)) {
            return false;
        }
        return dayOfWeek == other.dayOfWeek && Objects.equals(opensAt, other.opensAt)
                && Objects.equals(closesAt, other.closesAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(dayOfWeek, opensAt, closesAt);
    }
}
