package net.talaatharb.activitydag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import net.talaatharb.activitydag.model.ActivityModel;
import net.talaatharb.activitydag.planning.CpmStrategy;
import net.talaatharb.activitydag.planning.HighImpactStrategy;
import net.talaatharb.activitydag.planning.LowestResourcesStrategy;
import net.talaatharb.activitydag.planning.MaximumResourcesStrategy;
import net.talaatharb.activitydag.planning.PlanResult;
import net.talaatharb.activitydag.planning.PlannedActivity;

class PlanningStrategiesTest {
    private static final LocalDate START = LocalDate.of(2026, 1, 1);

    private static ActivityModel activity(String name, long duration, int resources, ActivityModel... deps) {
        ActivityModel a = new ActivityModel();
        a.setId(UUID.randomUUID());
        a.setName(name);
        a.setDuration(duration);
        a.setResources(resources);
        for (ActivityModel d : deps) {
            a.getDependencies().add(d.getId());
        }
        return a;
    }

    private static PlannedActivity find(PlanResult r, String name) {
        return r.activities().stream().filter(p -> p.name().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void cpmFindsCriticalPathAndSlack() {
        ActivityModel a = activity("A", 3, 1);
        ActivityModel b = activity("B", 2, 1, a);
        ActivityModel c = activity("C", 5, 1, a);
        ActivityModel d = activity("D", 1, 1, b, c);
        PlanResult r = new CpmStrategy().plan(List.of(d, c, b, a), START);

        assertEquals(9, r.totalDays());
        assertTrue(find(r, "A").critical());
        assertTrue(find(r, "C").critical());
        assertTrue(find(r, "D").critical());
        assertFalse(find(r, "B").critical());
        assertEquals(3, find(r, "B").slack());
        assertEquals(START.plusDays(3), find(r, "C").startDate());
        assertEquals(START.plusDays(9), find(r, "D").endDate());
    }

    @Test
    void lowestResourcesSerialisesParallelWork() {
        ActivityModel a = activity("A", 2, 3);
        ActivityModel b = activity("B", 2, 3);
        ActivityModel c = activity("C", 2, 3);
        List<ActivityModel> all = List.of(a, b, c);

        PlanResult max = new MaximumResourcesStrategy().plan(all, START);
        PlanResult low = new LowestResourcesStrategy().plan(all, START);

        assertEquals(2, max.totalDays());
        assertEquals(9, max.peakResources());
        assertEquals(6, low.totalDays());
        assertEquals(3, low.peakResources());
    }

    @Test
    void lowestResourcesRespectsDependencies() {
        ActivityModel a = activity("A", 2, 2);
        ActivityModel b = activity("B", 0, 0, a);
        ActivityModel c = activity("C", 1, 1, b);
        PlanResult r = new LowestResourcesStrategy().plan(List.of(a, b, c), START);
        assertEquals(3, r.totalDays());
        assertEquals(2, find(r, "C").startOffset());
    }

    @Test
    void cycleIsRejected() {
        ActivityModel a = activity("A", 1, 1);
        ActivityModel b = activity("B", 1, 1, a);
        a.getDependencies().add(b.getId());
        assertThrows(IllegalStateException.class, () -> new CpmStrategy().plan(List.of(a, b), START));
    }

    @Test
    void highImpactStrategyPrioritisesImpactAndComputesPercent() {
        ActivityModel low = activity("Low", 2, 3);
        low.setImpact(10);
        ActivityModel high = activity("High", 2, 3);
        high.setImpact(30);
        List<ActivityModel> all = List.of(low, high);

        PlanResult r = new HighImpactStrategy().plan(all, START);
        assertEquals(0, find(r, "High").startOffset());
        assertEquals(2, find(r, "Low").startOffset());
        assertEquals(75.0, find(r, "High").impactPercent(), 0.001);
        assertEquals(25.0, find(r, "Low").impactPercent(), 0.001);
    }

    @Test
    void highImpactStrategyBoostsPrerequisitesOfHighImpactWork() {
        ActivityModel prereq = activity("Z-prereq", 1, 2);
        ActivityModel big = activity("Big", 1, 2, prereq);
        big.setImpact(50);
        ActivityModel other = activity("A-other", 1, 2);
        other.setImpact(5);
        PlanResult r = new HighImpactStrategy().plan(List.of(other, big, prereq), START);
        assertEquals(0, find(r, "Z-prereq").startOffset());
        assertEquals(1, find(r, "Big").startOffset());
        assertEquals(2, find(r, "A-other").startOffset());
    }
}
