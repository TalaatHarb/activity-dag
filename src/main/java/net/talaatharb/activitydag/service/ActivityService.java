package net.talaatharb.activitydag.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import net.talaatharb.activitydag.model.ActivityModel;
import net.talaatharb.activitydag.persistence.ActivityRepository;
import net.talaatharb.activitydag.planning.DagSupport;

@Singleton
public class ActivityService {
    private final ActivityRepository repository;

    @Inject
    public ActivityService(ActivityRepository repository) {
        this.repository = repository;
    }

    public List<ActivityModel> findByProject(UUID projectId) {
        return repository.findByProjectId(projectId);
    }

    public Optional<ActivityModel> findById(UUID id) {
        return repository.findById(id);
    }

    /** Validates and stores an activity (create or update). */
    public ActivityModel save(ActivityModel activity) {
        if (activity.getProjectId() == null) {
            throw new IllegalArgumentException("Activity must belong to a project");
        }
        if (activity.getName() == null || activity.getName().isBlank()) {
            throw new IllegalArgumentException("Activity name is required");
        }
        if (activity.getDuration() < 0) {
            throw new IllegalArgumentException("Duration must not be negative");
        }
        if (activity.getResources() < 0) {
            throw new IllegalArgumentException("Resources must not be negative");
        }
        if (activity.getStartDate() != null && activity.getEndDate() != null
                && activity.getEndDate().isBefore(activity.getStartDate())) {
            throw new IllegalArgumentException("End date must not be before start date");
        }
        List<ActivityModel> siblings = new ArrayList<>(repository.findByProjectId(activity.getProjectId()));
        siblings.removeIf(a -> a.getId().equals(activity.getId()));
        Set<UUID> validIds = new java.util.HashSet<>();
        siblings.forEach(a -> validIds.add(a.getId()));
        for (UUID dep : activity.getDependencies()) {
            if (!validIds.contains(dep)) {
                throw new IllegalArgumentException("Dependency does not exist in this project: " + dep);
            }
        }
        if (activity.getId() != null) {
            siblings.add(activity);
            try {
                DagSupport.topologicalOrder(siblings);
            } catch (IllegalStateException e) {
                throw new IllegalArgumentException("Dependencies would create a cycle", e);
            }
        }
        return repository.save(activity);
    }

    /** Ids of all activities that directly or indirectly depend on the given activity. */
    public Set<UUID> findDependents(UUID activityId) {
        return repository.findById(activityId)
                .map(a -> DagSupport.transitiveDependents(activityId, repository.findByProjectId(a.getProjectId())))
                .orElse(Set.of());
    }

    /** Deletes the activity and removes it from the dependencies of the remaining activities. */
    public void delete(UUID activityId) {
        ActivityModel activity = repository.findById(activityId).orElse(null);
        if (activity == null) {
            return;
        }
        for (ActivityModel other : repository.findByProjectId(activity.getProjectId())) {
            if (other.getDependencies().remove(activityId)) {
                repository.save(other);
            }
        }
        repository.deleteById(activityId);
    }

    /** Stores planned start and end dates on the given activities. */
    public void applySchedule(Map<UUID, LocalDate[]> schedule) {
        schedule.forEach((id, dates) -> repository.findById(id).ifPresent(a -> {
            a.setStartDate(dates[0]);
            a.setEndDate(dates[1]);
            repository.save(a);
        }));
    }
}
