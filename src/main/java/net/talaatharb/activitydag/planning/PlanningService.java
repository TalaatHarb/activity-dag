package net.talaatharb.activitydag.planning;

import java.time.LocalDate;
import java.util.List;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import net.talaatharb.activitydag.model.ActivityModel;

@Singleton
public class PlanningService {
    private final List<PlanningStrategy> strategies;

    @Inject
    public PlanningService(CpmStrategy cpm, LowestResourcesStrategy lowest, MaximumResourcesStrategy maximum) {
        this.strategies = List.of(cpm, lowest, maximum);
    }

    public List<String> getStrategyNames() {
        return strategies.stream().map(PlanningStrategy::getName).toList();
    }

    public PlanResult plan(String strategyName, List<ActivityModel> activities, LocalDate projectStart) {
        PlanningStrategy strategy = strategies.stream().filter(s -> s.getName().equals(strategyName)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown strategy: " + strategyName));
        return strategy.plan(activities, projectStart);
    }
}
