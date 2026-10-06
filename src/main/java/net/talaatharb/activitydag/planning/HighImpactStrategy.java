package net.talaatharb.activitydag.planning;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.google.inject.Singleton;

import net.talaatharb.activitydag.model.ActivityModel;

/**
 * Resource-levelled schedule (same resource cap as {@link LowestResourcesStrategy}) that gives scarce capacity to
 * the activities with the highest impact percentage first (activity impact / total project impact). An activity
 * inherits the highest impact percentage among itself and everything that depends on it, so the prerequisites of
 * high-impact work are not starved.
 */
@Singleton
public class HighImpactStrategy extends LowestResourcesStrategy {
    @Override
    public String getName() {
        return "High impact first";
    }

    @Override
    protected Comparator<ActivityModel> priority(List<ActivityModel> activities, CriticalPath cpm) {
        double total = activities.stream().mapToDouble(a -> Math.max(0, a.getImpact())).sum();
        Map<UUID, Double> effective = new HashMap<>();
        for (ActivityModel a : activities) {
            double best = PlanAssembler.impactPercent(a, total);
            for (UUID dependentId : DagSupport.transitiveDependents(a.getId(), activities)) {
                for (ActivityModel d : activities) {
                    if (d.getId().equals(dependentId)) {
                        best = Math.max(best, PlanAssembler.impactPercent(d, total));
                    }
                }
            }
            effective.put(a.getId(), best);
        }
        return Comparator.comparingDouble((ActivityModel a) -> -effective.get(a.getId()))
                .thenComparingInt(a -> cpm.times(a.getId()).lateFinish())
                .thenComparing(a -> a.getName() == null ? "" : a.getName());
    }
}
