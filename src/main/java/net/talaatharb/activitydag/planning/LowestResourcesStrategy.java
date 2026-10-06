package net.talaatharb.activitydag.planning;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.google.inject.Singleton;

import net.talaatharb.activitydag.model.ActivityModel;

/**
 * Resource levelling: caps concurrent resource usage at the smallest feasible value (the largest single activity
 * requirement) and schedules ready activities by least latest-finish first, trading duration for resources.
 */
@Singleton
public class LowestResourcesStrategy implements PlanningStrategy {
    @Override
    public String getName() {
        return "Lowest resources";
    }

    @Override
    public PlanResult plan(List<ActivityModel> activities, LocalDate projectStart) {
        CriticalPath cpm = new CriticalPath(activities);
        int cap = activities.stream().mapToInt(ActivityModel::getResources).max().orElse(0);
        Comparator<ActivityModel> priority = priority(activities, cpm);

        Map<UUID, Integer> start = new HashMap<>();
        Map<UUID, Integer> end = new HashMap<>();
        Set<ActivityModel> remaining = new LinkedHashSet<>(DagSupport.topologicalOrder(activities));
        int time = 0;
        while (!remaining.isEmpty()) {
            int running = 0;
            for (ActivityModel a : activities) {
                Integer s = start.get(a.getId());
                if (s != null && s <= time && time < end.get(a.getId())) {
                    running += a.getResources();
                }
            }
            boolean started = true;
            while (started) {
                started = false;
                final int now = time;
                List<ActivityModel> ready = new ArrayList<>();
                for (ActivityModel a : remaining) {
                    if (a.getDependencies().stream().allMatch(d -> !isKnown(activities, d)
                            || (end.containsKey(d) && end.get(d) <= now))) {
                        ready.add(a);
                    }
                }
                ready.sort(priority);
                for (ActivityModel a : ready) {
                    int duration = (int) Math.max(0, a.getDuration());
                    if (duration == 0 || running + a.getResources() <= cap) {
                        start.put(a.getId(), time);
                        end.put(a.getId(), time + duration);
                        remaining.remove(a);
                        running += duration == 0 ? 0 : a.getResources();
                        started = true;
                        break;
                    }
                }
            }
            if (remaining.isEmpty()) {
                break;
            }
            int next = Integer.MAX_VALUE;
            for (int e : end.values()) {
                if (e > time) {
                    next = Math.min(next, e);
                }
            }
            if (next == Integer.MAX_VALUE) {
                throw new IllegalStateException("Unable to schedule remaining activities");
            }
            time = next;
        }
        return PlanAssembler.assemble(activities, start, cpm, projectStart);
    }

    /** Order in which ready activities compete for the resource cap; earlier wins. */
    protected Comparator<ActivityModel> priority(List<ActivityModel> activities, CriticalPath cpm) {
        return Comparator.comparingInt((ActivityModel a) -> cpm.times(a.getId()).lateFinish())
                .thenComparing(a -> a.getName() == null ? "" : a.getName());
    }

    private static boolean isKnown(List<ActivityModel> activities, UUID id) {
        return activities.stream().anyMatch(a -> a.getId().equals(id));
    }
}
