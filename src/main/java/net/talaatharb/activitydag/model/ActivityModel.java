package net.talaatharb.activitydag.model;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** A unit of work belonging to a project; dependencies form a DAG. Duration is an amount of {@link #durationUnit}. */
public class ActivityModel extends BaseModel {
    private static final long serialVersionUID = 3L;

    private UUID projectId;
    private String name;
    private String description;
    private Set<UUID> dependencies = new HashSet<>();
    private long duration;
    private DurationUnit durationUnit = DurationUnit.DAYS;
    private Instant startDate;
    private Instant endDate;
    private int resources;
    private int impact;
    private String status;
    private String category;
    private Map<String, String> metadata = new HashMap<>();
    private Set<String> tags = new LinkedHashSet<>();

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
    public DurationUnit getDurationUnit() { return durationUnit; }
    public void setDurationUnit(DurationUnit durationUnit) { this.durationUnit = durationUnit; }

    /** Duration converted to minutes, never negative. */
    public long durationInMinutes() {
        DurationUnit unit = durationUnit == null ? DurationUnit.DAYS : durationUnit;
        return unit.toMinutes(Math.max(0, duration));
    }
    public Instant getStartDate() { return startDate; }
    public void setStartDate(Instant startDate) { this.startDate = startDate; }
    public Instant getEndDate() { return endDate; }
    public void setEndDate(Instant endDate) { this.endDate = endDate; }
    public int getResources() { return resources; }
    public void setResources(int resources) { this.resources = resources; }
    public int getImpact() { return impact; }
    public void setImpact(int impact) { this.impact = impact; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public Map<String, String> getMetadata() { return metadata; }
    public void setMetadata(Map<String, String> metadata) { this.metadata = metadata; }
    public Set<String> getTags() { return tags; }
    public void setTags(Set<String> tags) { this.tags = tags == null ? new LinkedHashSet<>() : tags; }
}
