package net.talaatharb.activitydag.model;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** A unit of work belonging to a project; dependencies form a DAG. Duration is expressed in days. */
public class ActivityModel extends BaseModel {
    private static final long serialVersionUID = 1L;

    private UUID projectId;
    private String name;
    private String description;
    private Set<UUID> dependencies = new HashSet<>();
    private long duration;
    private LocalDate startDate;
    private LocalDate endDate;
    private int resources;
    private Map<String, String> metadata = new HashMap<>();

    public UUID getProjectId() { return projectId; }
    public void setProjectId(UUID projectId) { this.projectId = projectId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Set<UUID> getDependencies() { return dependencies; }
    public void setDependencies(Set<UUID> dependencies) { this.dependencies = dependencies; }
    public long getDuration() { return duration; }
    public void setDuration(long duration) { this.duration = duration; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public int getResources() { return resources; }
    public void setResources(int resources) { this.resources = resources; }
    public Map<String, String> getMetadata() { return metadata; }
    public void setMetadata(Map<String, String> metadata) { this.metadata = metadata; }
}
