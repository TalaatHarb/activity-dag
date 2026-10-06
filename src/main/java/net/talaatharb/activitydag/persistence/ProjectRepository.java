package net.talaatharb.activitydag.persistence;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import net.talaatharb.activitydag.model.ProjectModel;

@Singleton
public class ProjectRepository extends MapDbRepository<ProjectModel> {
    @Inject
    public ProjectRepository(MapDbStorage storage) {
        super(storage, "projects");
    }
}
