package net.talaatharb.activitydag.planning;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.talaatharb.activitydag.model.ActivityModel;

/** Graph helpers over activities. Dependencies pointing outside the given collection are ignored. */
public final class DagSupport {
    private DagSupport() {
    }

    /** Returns activities ordered so that every activity comes after its dependencies. */
    public static List<ActivityModel> topologicalOrder(Collection<ActivityModel> activities) {
        Map<UUID, ActivityModel> byId = new HashMap<>();
        activities.forEach(a -> byId.put(a.getId(), a));
        Map<UUID, Integer> pending = new HashMap<>();
        Map<UUID, List<UUID>> successors = new HashMap<>();
        for (ActivityModel a : activities) {
            int count = 0;
            for (UUID dep : a.getDependencies()) {
                if (byId.containsKey(dep)) {
                    count++;
                    successors.computeIfAbsent(dep, k -> new ArrayList<>()).add(a.getId());
                }
            }
            pending.put(a.getId(), count);
        }
        Deque<UUID> ready = new ArrayDeque<>();
        activities.forEach(a -> {
            if (pending.get(a.getId()) == 0) {
                ready.add(a.getId());
            }
        });
        List<ActivityModel> order = new ArrayList<>();
        while (!ready.isEmpty()) {
            UUID id = ready.poll();
            order.add(byId.get(id));
            for (UUID next : successors.getOrDefault(id, List.of())) {
                if (pending.merge(next, -1, Integer::sum) == 0) {
                    ready.add(next);
                }
            }
        }
        if (order.size() != byId.size()) {
            throw new IllegalStateException("Activity dependencies contain a cycle");
        }
        return order;
    }

    /** All activities that directly or indirectly depend on the given activity. */
    public static Set<UUID> transitiveDependents(UUID id, Collection<ActivityModel> activities) {
        Set<UUID> result = new HashSet<>();
        Deque<UUID> queue = new ArrayDeque<>();
        queue.add(id);
        while (!queue.isEmpty()) {
            UUID current = queue.poll();
            for (ActivityModel a : activities) {
                if (a.getDependencies().contains(current) && result.add(a.getId())) {
                    queue.add(a.getId());
                }
            }
        }
        return result;
    }
}
