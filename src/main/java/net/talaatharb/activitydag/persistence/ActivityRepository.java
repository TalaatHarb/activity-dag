package net.talaatharb.activitydag.persistence;

import java.util.List;
import java.util.UUID;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import net.talaatharb.activitydag.model.ActivityModel;

@Singleton
public class ActivityRepository extends MapDbRepository<ActivityModel> {
    @Inject
    public ActivityRepository(MapDbStorage storage) {
        super(storage, "activities");
    }

    public List<ActivityModel> findByProjectId(UUID projectId) {
        return findAll().stream().filter(a -> projectId.equals(a.getProjectId())).toList();
    }

    public void deleteByProjectId(UUID projectId) {
        deleteAll(findByProjectId(projectId).stream().map(ActivityModel::getId).toList());
    }
}
