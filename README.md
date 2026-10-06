# activity-dag
A JavaFX desktop application for activity planning that treats activities as a directed acyclic graph and allows planning using critical path method and resource allocations.


## Features
- **Activities tab**: define, view, edit (including dependencies, metadata) and delete activities; deleting an activity that others depend on shows a warning.
- **Graph tab**: DAG view of the current project's activities.
- **Planning tab**: CPM, lowest-resources (levelled) and maximum-resources strategies; plans can be applied back to the activities' start/end dates.
- Multiple projects, switchable from the bar on top.

## Technology
JavaFX + FXML (MVC), Guice (dependency injection), MapDB (persistence file, default `~/.activity-dag/activity-dag.db`, override with `-Dactivitydag.db=<path>`), MapStruct (model ↔ DTO).

Build and test: `mvn verify`; run: `mvn javafx:run`.
