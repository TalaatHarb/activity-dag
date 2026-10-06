package net.talaatharb.activitydag.planning;

import java.util.List;

/** Outcome of a planning strategy: a schedule plus summary figures. */
public record PlanResult(List<PlannedActivity> activities, int totalDays, int peakResources) {
}
