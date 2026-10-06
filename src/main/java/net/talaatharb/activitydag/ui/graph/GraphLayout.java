package net.talaatharb.activitydag.ui.graph;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.talaatharb.activitydag.dto.ActivityDto;

/** Layered DAG layout: column = longest dependency chain leading to the activity, row = order inside column. */
public final class GraphLayout {
    public record Cell(int layer, int row) {
    }

    private GraphLayout() {
    }

    public static Map<UUID, Cell> compute(List<ActivityDto> activities) {
        Map<UUID, ActivityDto> byId = new HashMap<>();
        activities.forEach(a -> byId.put(a.getId(), a));
        Map<UUID, Integer> layers = new HashMap<>();
        for (ActivityDto a : activities) {
            layerOf(a.getId(), byId, layers, new java.util.HashSet<>());
        }
        Map<Integer, Integer> rowCounters = new HashMap<>();
        Map<UUID, Cell> cells = new HashMap<>();
        activities.stream()
                .sorted(Comparator.comparingInt((ActivityDto a) -> layers.get(a.getId()))
                        .thenComparing(a -> a.getName() == null ? "" : a.getName()))
                .forEach(a -> {
                    int layer = layers.get(a.getId());
                    int row = rowCounters.merge(layer, 1, Integer::sum) - 1;
                    cells.put(a.getId(), new Cell(layer, row));
                });
        return cells;
    }

    private static int layerOf(UUID id, Map<UUID, ActivityDto> byId, Map<UUID, Integer> memo,
            java.util.Set<UUID> visiting) {
        Integer known = memo.get(id);
        if (known != null) {
            return known;
        }
        if (!visiting.add(id)) {
            return 0; // defensive: cycles are rejected on save
        }
        int layer = 0;
        for (UUID dep : byId.get(id).getDependencies()) {
            if (byId.containsKey(dep)) {
                layer = Math.max(layer, layerOf(dep, byId, memo, visiting) + 1);
            }
        }
        visiting.remove(id);
        memo.put(id, layer);
        return layer;
    }
}
