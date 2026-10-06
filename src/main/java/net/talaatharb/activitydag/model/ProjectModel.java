package net.talaatharb.activitydag.model;

/** A project groups a set of activities. */
public class ProjectModel extends BaseModel {
    private static final long serialVersionUID = 1L;

    private String name;
    private String description;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
