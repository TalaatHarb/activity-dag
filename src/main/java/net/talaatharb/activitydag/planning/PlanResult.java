package net.talaatharb.activitydag.planning;

import java.util.List;

/** Outcome of a planning strategy: a schedule plus summary figures (total duration in minutes). */
public record PlanResult(List<PlannedActivity> activities, int totalMinutes, int peakResources) {
}
