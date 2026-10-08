package net.talaatharb.activitydag.ui;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
import net.talaatharb.activitydag.service.ActivityTransferService;
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
    private final ActivityTransferService transferService;
    private final ProjectMapper projectMapper;
    private final ActivityMapper activityMapper;

    private final ObservableList<ProjectDto> projects = FXCollections.observableArrayList();
    private final ObservableList<ActivityDto> activities = FXCollections.observableArrayList();
    private final ObservableList<ActivityDto> allActivities = FXCollections.observableArrayList();
    private final javafx.collections.ObservableSet<String> hiddenStatuses = FXCollections.observableSet();
    private final javafx.collections.ObservableSet<String> hiddenCategories = FXCollections.observableSet();
    private final ObjectProperty<ProjectDto> currentProject = new SimpleObjectProperty<>();

    @Inject
    public ProjectContext(ProjectService projectService, ActivityService activityService,
            PlanningService planningService, ActivityTransferService transferService, ProjectMapper projectMapper,
            ActivityMapper activityMapper) {
        this.projectService = projectService;
        this.activityService = activityService;
        this.planningService = planningService;
        this.transferService = transferService;
        this.projectMapper = projectMapper;
        this.activityMapper = activityMapper;
        currentProject.addListener((obs, old, now) -> reloadActivities());
        hiddenStatuses.addListener((javafx.collections.SetChangeListener<String>) c -> applyFilters());
        hiddenCategories.addListener((javafx.collections.SetChangeListener<String>) c -> applyFilters());
        if (projectService.findAll().isEmpty()) {
            createProject("Default project", "");
        }
        refreshProjects(null);
    }

    public ObservableList<ProjectDto> getProjects() { return projects; }
    /** Activities passing the status/category filters; this is what every view displays and calculates on. */
    public ObservableList<ActivityDto> getActivities() { return activities; }
    /** Every activity of the current project regardless of filters. */
    public ObservableList<ActivityDto> getAllActivities() { return allActivities; }
    /** Statuses ("" stands for none) whose activities are hidden. */
    public javafx.collections.ObservableSet<String> getHiddenStatuses() { return hiddenStatuses; }
    /** Categories ("" stands for none) whose activities are hidden. */
    public javafx.collections.ObservableSet<String> getHiddenCategories() { return hiddenCategories; }

    public static String key(String value) { return value == null ? "" : value.trim(); }

    private void applyFilters() {
        activities.setAll(allActivities.stream().filter(a -> !hiddenStatuses.contains(key(a.getStatus()))
                && !hiddenCategories.contains(key(a.getCategory()))).toList());
    }
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
            allActivities.clear();
        } else {
            allActivities.setAll(activityMapper.toDtos(activityService.findByProject(project.getId())));
        }
        applyFilters();
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

    /** Copies the current project and its activities (with fresh ids) under a new name and switches to it. */
    public ProjectDto duplicateCurrentProject(String newName) {
        ProjectDto source = currentProject.get();
        if (source == null) {
            throw new IllegalArgumentException("No project selected");
        }
        ProjectDto copy = projectMapper.toDto(transferService.duplicateProject(source.getId(), newName));
        refreshProjects(copy.getId());
        return copy;
    }

    public void exportActivities(Path file) throws IOException {
        transferService.exportToFile(currentProject.get().getId(), file);
    }

    public int importActivities(Path file) throws IOException {
        try {
            return transferService.importFromFile(currentProject.get().getId(), file);
        } finally {
            reloadActivities();
        }
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
        Set<UUID> visible = new HashSet<>();
        models.forEach(m -> visible.add(m.getId()));
        models.forEach(m -> m.setDependencies(new HashSet<>(m.getDependencies().stream().filter(visible::contains).toList())));
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
