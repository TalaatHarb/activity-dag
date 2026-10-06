package net.talaatharb.activitydag.planning;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.talaatharb.activitydag.model.ActivityModel;

/** Classic forward/backward pass computing early/late times in day offsets. */
final class CriticalPath {
    record Times(int earlyStart, int earlyFinish, int lateStart, int lateFinish) {
        int slack() {
            return lateStart - earlyStart;
        }
    }

    private final Map<UUID, Times> times = new HashMap<>();
    private final int projectDuration;

    CriticalPath(List<ActivityModel> activities) {
        List<ActivityModel> order = DagSupport.topologicalOrder(activities);
        Map<UUID, Integer> es = new HashMap<>();
        Map<UUID, Integer> ef = new HashMap<>();
        int total = 0;
        for (ActivityModel a : order) {
            int start = 0;
            for (UUID dep : a.getDependencies()) {
                start = Math.max(start, ef.getOrDefault(dep, 0));
            }
            es.put(a.getId(), start);
            int finish = start + (int) Math.max(0, a.getDuration());
            ef.put(a.getId(), finish);
            total = Math.max(total, finish);
        }
        Map<UUID, Integer> lf = new HashMap<>();
        for (ActivityModel a : order) {
            lf.put(a.getId(), total);
        }
        for (int i = order.size() - 1; i >= 0; i--) {
            ActivityModel a = order.get(i);
            int latestFinish = lf.get(a.getId());
            int latestStart = latestFinish - (int) Math.max(0, a.getDuration());
            times.put(a.getId(), new Times(es.get(a.getId()), ef.get(a.getId()), latestStart, latestFinish));
            for (UUID dep : a.getDependencies()) {
                if (lf.containsKey(dep)) {
                    lf.merge(dep, latestStart, Math::min);
                }
            }
        }
        this.projectDuration = total;
    }

    Times times(UUID id) {
        return times.get(id);
    }

    int projectDuration() {
        return projectDuration;
    }
}
