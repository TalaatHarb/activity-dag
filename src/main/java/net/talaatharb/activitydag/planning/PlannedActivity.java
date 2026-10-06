package net.talaatharb.activitydag.planning;

import java.time.LocalDate;
import java.util.UUID;

public record PlannedActivity(UUID id, String name, int startOffset, int endOffset, LocalDate startDate,
        LocalDate endDate, int resources, int slack, boolean critical) {
}
