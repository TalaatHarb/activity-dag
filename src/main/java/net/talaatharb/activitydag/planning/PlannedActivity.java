package net.talaatharb.activitydag.planning;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/** A scheduled activity; offsets and slack are in minutes from the project start (00:00 of the start date). */
public record PlannedActivity(UUID id, String name, int startOffset, int endOffset, LocalDateTime start,
        LocalDateTime end, int resources, double impactPercent, int slack, boolean critical) {

    public LocalDate startDate() {
        return start.toLocalDate();
    }

    public LocalDate endDate() {
        return end.toLocalDate();
    }
}
