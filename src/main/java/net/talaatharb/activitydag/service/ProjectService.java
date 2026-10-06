package net.talaatharb.activitydag.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import net.talaatharb.activitydag.model.ProjectModel;
import net.talaatharb.activitydag.persistence.ActivityRepository;
import net.talaatharb.activitydag.persistence.ProjectRepository;

@Singleton
public class ProjectService {
    private final ProjectRepository projects;
    private final ActivityRepository activities;

    @Inject
    public ProjectService(ProjectRepository projects, ActivityRepository activities) {
        this.projects = projects;
        this.activities = activities;
    }

    public ProjectModel save(ProjectModel project) {
        if (project.getName() == null || project.getName().isBlank()) {
            throw new IllegalArgumentException("Project name is required");
        }
        return projects.save(project);
    }

    public List<ProjectModel> findAll() {
        return projects.findAll();
    }

    public Optional<ProjectModel> findById(UUID id) {
        return projects.findById(id);
    }

    /** Deletes the project together with all of its activities. */
    public void delete(UUID id) {
        activities.deleteByProjectId(id);
        projects.deleteById(id);
    }
}
