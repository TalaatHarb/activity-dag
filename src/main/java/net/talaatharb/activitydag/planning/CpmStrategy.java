package net.talaatharb.activitydag.planning;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.google.inject.Singleton;

import net.talaatharb.activitydag.model.ActivityModel;

/** Critical Path Method: earliest-start schedule; activities with zero slack are flagged critical. */
@Singleton
public class CpmStrategy implements PlanningStrategy {
    @Override
    public String getName() {
        return "Critical Path Method (CPM)";
    }

    @Override
    public PlanResult plan(List<ActivityModel> activities, LocalDate projectStart) {
        CriticalPath cpm = new CriticalPath(activities);
        Map<UUID, Integer> starts = new HashMap<>();
        activities.forEach(a -> starts.put(a.getId(), cpm.times(a.getId()).earlyStart()));
        return PlanAssembler.assemble(activities, starts, cpm, projectStart);
    }
}
