package net.talaatharb.activitydag.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import net.talaatharb.activitydag.model.ActivityModel;
import net.talaatharb.activitydag.model.DurationUnit;
import net.talaatharb.activitydag.model.ProjectModel;

/**
 * Copies activities between projects and to/from JSON. Copies never reuse ids: every activity gets a fresh id and
 * dependencies are remapped. In JSON, ids are omitted and dependencies are referenced by activity name.
 */
@Singleton
public class ActivityTransferService {
    /** JSON shape of one activity. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ActivityJson(String name, String description, long duration, DurationUnit durationUnit,
            LocalDate startDate, LocalDate endDate, int resources, int impact, Map<String, String> metadata,
            List<String> dependsOn, String status, String category) {
    }

    private final ProjectService projectService;
    private final ActivityService activityService;
    private final ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).enable(SerializationFeature.INDENT_OUTPUT);

    @Inject
    public ActivityTransferService(ProjectService projectService, ActivityService activityService) {
        this.projectService = projectService;
        this.activityService = activityService;
    }

    /** Creates a new project with the given name holding copies (with fresh ids) of the source's activities. */
    public ProjectModel duplicateProject(UUID sourceProjectId, String newName) {
        ProjectModel source = projectService.findById(sourceProjectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found"));
        ProjectModel copy = new ProjectModel();
        copy.setName(newName);
        copy.setDescription(source.getDescription());
        List<ActivityModel> activities = copyWithFreshIds(activityService.findByProject(sourceProjectId));
        copy = projectService.save(copy);
        try {
            activityService.saveAll(copy.getId(), activities);
        } catch (RuntimeException e) {
            projectService.delete(copy.getId());
            throw e;
        }
        return copy;
    }

    /** Deep copies of the given activities with new ids; dependencies outside the given list are dropped. */
    static List<ActivityModel> copyWithFreshIds(List<ActivityModel> source) {
        Map<UUID, UUID> newIds = new HashMap<>();
        source.forEach(a -> newIds.put(a.getId(), UUID.randomUUID()));
        List<ActivityModel> result = new ArrayList<>();
        for (ActivityModel a : source) {
            ActivityModel c = new ActivityModel();
            c.setId(newIds.get(a.getId()));
            c.setName(a.getName());
            c.setDescription(a.getDescription());
            c.setDuration(a.getDuration());
            c.setDurationUnit(a.getDurationUnit());
            c.setStartDate(a.getStartDate());
            c.setEndDate(a.getEndDate());
            c.setResources(a.getResources());
            c.setImpact(a.getImpact());
            c.setStatus(a.getStatus());
            c.setCategory(a.getCategory());
            c.setMetadata(new HashMap<>(a.getMetadata()));
            Set<UUID> deps = new HashSet<>();
            a.getDependencies().stream().map(newIds::get).filter(id -> id != null).forEach(deps::add);
            c.setDependencies(deps);
            result.add(c);
        }
        return result;
    }

    public String exportJson(UUID projectId) {
        List<ActivityModel> activities = activityService.findByProject(projectId);
        Map<UUID, String> names = new HashMap<>();
        Set<String> seen = new HashSet<>();
        for (ActivityModel a : activities) {
            if (!seen.add(a.getName())) {
                throw new IllegalArgumentException("Cannot export: activity names must be unique because "
                        + "dependencies are exported by name. Duplicate: " + a.getName());
            }
            names.put(a.getId(), a.getName());
        }
        List<ActivityJson> out = activities.stream().map(a -> new ActivityJson(a.getName(), a.getDescription(),
                a.getDuration(), a.getDurationUnit(), a.getStartDate(), a.getEndDate(), a.getResources(),
                a.getImpact(), new LinkedHashMap<>(a.getMetadata()),
                a.getDependencies().stream().map(names::get).filter(n -> n != null).sorted().toList(), a.getStatus(), a.getCategory())).toList();
        try {
            return json.writeValueAsString(out);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialise activities", e);
        }
    }

    /** Adds the activities described by the JSON array to the project and returns how many were imported. */
    public int importJson(UUID projectId, String text) {
        List<ActivityJson> in;
        try {
            in = json.readValue(text, new TypeReference<List<ActivityJson>>() {
            });
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid activities JSON: " + e.getOriginalMessage(), e);
        }
        if (in == null) {
            throw new IllegalArgumentException("Invalid activities JSON: expected an array");
        }
        Map<String, UUID> ids = new HashMap<>();
        for (ActivityJson j : in) {
            if (j == null || j.name() == null || j.name().isBlank()) {
                throw new IllegalArgumentException("Every imported activity needs a name");
            }
            if (ids.put(j.name(), UUID.randomUUID()) != null) {
                throw new IllegalArgumentException("Duplicate activity name in file: " + j.name());
            }
        }
        List<ActivityModel> batch = new ArrayList<>();
        for (ActivityJson j : in) {
            ActivityModel a = new ActivityModel();
            a.setId(ids.get(j.name()));
            a.setName(j.name());
            a.setDescription(j.description());
            a.setDuration(j.duration());
            a.setDurationUnit(j.durationUnit() == null ? DurationUnit.DAYS : j.durationUnit());
            a.setStartDate(j.startDate());
            a.setEndDate(j.endDate());
            a.setResources(j.resources());
            a.setImpact(j.impact());
            a.setStatus(j.status());
            a.setCategory(j.category());
            a.setMetadata(j.metadata() == null ? new HashMap<>() : new HashMap<>(j.metadata()));
            Set<UUID> deps = new HashSet<>();
            for (String dep : j.dependsOn() == null ? List.<String>of() : j.dependsOn()) {
                UUID id = ids.get(dep);
                if (id == null) {
                    throw new IllegalArgumentException(
                            "'" + j.name() + "' depends on '" + dep + "', which is not in the file");
                }
                deps.add(id);
            }
            a.setDependencies(deps);
            batch.add(a);
        }
        activityService.saveAll(projectId, batch);
        return batch.size();
    }

    public void exportToFile(UUID projectId, Path file) throws IOException {
        Files.writeString(file, exportJson(projectId));
    }

    public int importFromFile(UUID projectId, Path file) throws IOException {
        return importJson(projectId, Files.readString(file));
    }
}
