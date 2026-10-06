package net.talaatharb.activitydag.planning;

import java.time.LocalDate;
import java.util.List;

import net.talaatharb.activitydag.model.ActivityModel;

public interface PlanningStrategy {
    String getName();

    PlanResult plan(List<ActivityModel> activities, LocalDate projectStart);
}
