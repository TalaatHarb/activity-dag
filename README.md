# activity-dag
A JavaFX desktop application for activity planning that treats activities as a directed acyclic graph and allows planning using critical path method and resource allocations.


## Features
- **Activities tab**: define, view, edit and delete activities. Dependencies are picked with checkboxes (activities that depend on the edited one are hidden to prevent cycles), metadata is edited as key/value rows, tags as removable badges, start/end are instants (date + HH:mm, local time zone), and duration is an amount plus a unit (minutes, hours, days, weeks); deleting an activity that others depend on shows a warning.
- **Kanban tab**: activities are cards showing the title, tags as badges and the status or category (whichever is not used for the columns); drag cards between columns.
- **Calendar tab** (built on [CalendarFX](https://github.com/dlsc-software-consulting-gmbh/CalendarFX)): 3-day (yesterday/today/tomorrow by default, stepped one day at a time), week and month views; drag activities to move them or drag their top/bottom edge to change start/end time. Only activities with a start or end date are shown.
- **Graph tab**: DAG view of the current project's activities.
- **Planning tab**: CPM, lowest-resources (levelled), maximum-resources and high-impact-first (by impact percentage) strategies, scheduled with minute precision on continuous calendar time (24h days) and shown as a Gantt chart (hour/day/week axis; critical activities in red, slack as a grey line) plus an ordered table; plans can be applied back to the activities' start/end dates.
- **Import / export** (Activities tab): save the current project's activities to a JSON array file, or add activities from such a file. IDs are not exported; dependencies are referenced by name (`"dependsOn": ["A"]`), so activity names must be unique within a file. Imports are validated as a whole (unknown dependencies, cycles, invalid values) before anything is saved.
- Multiple projects, switchable from the bar on top; **Duplicate** copies the current project and all its activities under a new name, with fresh IDs.

## Technology
JavaFX + FXML (MVC), Guice (dependency injection), MapDB (persistence file, default `~/.activity-dag/activity-dag.db`, override with `-Dactivitydag.db=<path>`), MapStruct (model ↔ DTO), Jackson (JSON import/export).

Build and test: `mvn verify`; run: `mvn javafx:run`.
