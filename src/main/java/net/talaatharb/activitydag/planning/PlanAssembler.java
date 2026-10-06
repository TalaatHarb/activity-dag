package net.talaatharb.activitydag.planning;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.talaatharb.activitydag.model.ActivityModel;

/** Builds a {@link PlanResult} from chosen start offsets. */
final class PlanAssembler {
    private PlanAssembler() {
    }

    static PlanResult assemble(List<ActivityModel> activities, Map<UUID, Integer> startOffsets, CriticalPath cpm,
            LocalDate projectStart) {
        List<PlannedActivity> planned = new ArrayList<>();
        int total = 0;
        for (ActivityModel a : DagSupport.topologicalOrder(activities)) {
            int start = startOffsets.get(a.getId());
            int end = start + (int) Math.max(0, a.getDuration());
            total = Math.max(total, end);
            int slack = cpm.times(a.getId()).slack();
            planned.add(new PlannedActivity(a.getId(), a.getName(), start, end, projectStart.plusDays(start),
                    projectStart.plusDays(end), a.getResources(), slack, slack == 0));
        }
        int peak = 0;
        for (PlannedActivity p : planned) {
            int load = 0;
            for (PlannedActivity q : planned) {
                if (q.startOffset() <= p.startOffset() && p.startOffset() < q.endOffset()) {
                    load += q.resources();
                }
            }
            peak = Math.max(peak, load);
        }
        return new PlanResult(planned, total, peak);
    }
}
