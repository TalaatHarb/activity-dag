package net.talaatharb.activitydag.ui;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import net.talaatharb.activitydag.dto.ActivityDto;
import net.talaatharb.activitydag.dto.ProjectDto;
import net.talaatharb.activitydag.mapper.ActivityMapper;
import net.talaatharb.activitydag.mapper.ProjectMapper;
import net.talaatharb.activitydag.model.ActivityModel;
import net.talaatharb.activitydag.planning.PlanResult;
import net.talaatharb.activitydag.planning.PlannedActivity;
import net.talaatharb.activitydag.planning.PlanningService;
import net.talaatharb.activitydag.service.ActivityService;
import net.talaatharb.activitydag.service.ProjectService;

/**
 * Presentation model shared by all views: holds the observable projects, the current project and its activities,
 * and translates between DTOs (UI) and models (services).
 */
@Singleton
public class ProjectContext {
    private final ProjectService projectService;
    private final ActivityService activityService;
    private final PlanningService planningService;
    private final ProjectMapper projectMapper;
    private final ActivityMapper activityMapper;

    private final ObservableList<ProjectDto> projects = FXCollections.observableArrayList();
    private final ObservableList<ActivityDto> activities = FXCollections.observableArrayList();
    private final ObjectProperty<ProjectDto> currentProject = new SimpleObjectProperty<>();

    @Inject
    public ProjectContext(ProjectService projectService, ActivityService activityService,
            PlanningService planningService, ProjectMapper projectMapper, ActivityMapper activityMapper) {
        this.projectService = projectService;
        this.activityService = activityService;
        this.planningService = planningService;
        this.projectMapper = projectMapper;
        this.activityMapper = activityMapper;
        currentProject.addListener((obs, old, now) -> reloadActivities());
        if (projectService.findAll().isEmpty()) {
            createProject("Default project", "");
        }
        refreshProjects(null);
    }

    public ObservableList<ProjectDto> getProjects() { return projects; }
    public ObservableList<ActivityDto> getActivities() { return activities; }
    public ObjectProperty<ProjectDto> currentProjectProperty() { return currentProject; }
    public ProjectDto getCurrentProject() { return currentProject.get(); }

    public final void refreshProjects(UUID selectId) {
        UUID target = selectId != null ? selectId
                : currentProject.get() != null ? currentProject.get().getId() : null;
        List<ProjectDto> loaded = projectMapper.toDtos(projectService.findAll());
        projects.setAll(loaded);
        ProjectDto selected = loaded.stream().filter(p -> p.getId().equals(target)).findFirst()
                .orElse(loaded.isEmpty() ? null : loaded.get(0));
        currentProject.set(selected);
        reloadActivities();
    }

    public void reloadActivities() {
        ProjectDto project = currentProject.get();
        if (project == null) {
            activities.clear();
        } else {
            activities.setAll(activityMapper.toDtos(activityService.findByProject(project.getId())));
        }
    }

    public ProjectDto createProject(String name, String description) {
        ProjectDto dto = new ProjectDto();
        dto.setName(name);
        dto.setDescription(description);
        ProjectDto saved = projectMapper.toDto(projectService.save(projectMapper.toModel(dto)));
        if (!projects.isEmpty() || currentProject.get() != null) {
            refreshProjects(saved.getId());
        }
        return saved;
    }

    public void updateProject(ProjectDto dto) {
        projectService.save(projectMapper.toModel(dto));
        refreshProjects(dto.getId());
    }

    public void deleteCurrentProject() {
        ProjectDto project = currentProject.get();
        if (project == null) {
            return;
        }
        projectService.delete(project.getId());
        if (projectService.findAll().isEmpty()) {
            createProject("Default project", "");
        }
        refreshProjects(projectService.findAll().get(0).getId());
    }

    public ActivityDto saveActivity(ActivityDto dto) {
        dto.setProjectId(currentProject.get().getId());
        ActivityDto saved = activityMapper.toDto(activityService.save(activityMapper.toModel(dto)));
        reloadActivities();
        return saved;
    }

    public void deleteActivity(UUID id) {
        activityService.delete(id);
        reloadActivities();
    }

    /** Names of every activity that directly or indirectly depends on the given one. */
    public List<String> dependentNames(UUID id) {
        var ids = activityService.findDependents(id);
        return activities.stream().filter(a -> ids.contains(a.getId())).map(ActivityDto::getName).toList();
    }

    public PlanResult plan(String strategyName, LocalDate start) {
        List<ActivityModel> models = activityMapper.toModels(List.copyOf(activities));
        return planningService.plan(strategyName, models, start);
    }

    public void applyPlan(PlanResult result) {
        Map<UUID, LocalDate[]> schedule = result.activities().stream().collect(Collectors.toMap(
                PlannedActivity::id, p -> new LocalDate[] { p.startDate(), p.endDate() }));
        activityService.applySchedule(schedule);
        reloadActivities();
    }

    public List<String> strategyNames() {
        return planningService.getStrategyNames();
    }
}
