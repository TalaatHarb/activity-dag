package net.talaatharb.activitydag.ui;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.google.inject.Inject;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.cell.CheckBoxListCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;
import net.talaatharb.activitydag.dto.ActivityDto;
import net.talaatharb.activitydag.model.DurationUnit;

/** Define, view, edit and delete activities, including their dependencies. */
public class ActivityTabController {
    private static final String DEPENDENCY_HINT = "Tick the activities that must finish before this one starts.";

    private final ProjectContext context;
    private final Map<UUID, BooleanProperty> dependencyChecks = new HashMap<>();
    private UUID editingId;

    @FXML private TableView<ActivityDto> table;
    @FXML private TableColumn<ActivityDto, String> nameColumn;
    @FXML private TableColumn<ActivityDto, String> durationColumn;
    @FXML private TableColumn<ActivityDto, String> startColumn;
    @FXML private TableColumn<ActivityDto, String> endColumn;
    @FXML private TableColumn<ActivityDto, Number> resourcesColumn;
    @FXML private TableColumn<ActivityDto, Number> impactColumn;
    @FXML private TableColumn<ActivityDto, String> dependsOnColumn;
    @FXML private TextField nameField;
    @FXML private TextArea descriptionArea;
    @FXML private TextField durationField;
    @FXML private ComboBox<DurationUnit> durationUnitCombo;
    @FXML private TextField resourcesField;
    @FXML private TextField impactField;
    @FXML private TextField statusField;
    @FXML private TextField categoryField;
    @FXML private FilterBar filterBar;
    @FXML private TableColumn<ActivityDto, String> statusColumn;
    @FXML private TableColumn<ActivityDto, String> categoryColumn;
    @FXML private DatePicker startPicker;
    @FXML private DatePicker endPicker;
    @FXML private ListView<ActivityDto> dependenciesList;
    @FXML private Label dependenciesHint;
    @FXML private VBox metadataRows;
    @FXML private TextField startTimeField;
    @FXML private TextField endTimeField;
    @FXML private FlowPane tagsPane;
    @FXML private TextField tagField;
    private final Set<String> editTags = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    @FXML private Button deleteButton;

    @Inject
    public ActivityTabController(ProjectContext context) {
        this.context = context;
    }

    @FXML
    private void initialize() {
        filterBar.init(context);
        statusColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getStatus()));
        categoryColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getCategory()));
        nameColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getName()));
        durationColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().durationText()));
        startColumn.setCellValueFactory(c -> new SimpleStringProperty(formatInstant(c.getValue().getStartDate())));
        endColumn.setCellValueFactory(c -> new SimpleStringProperty(formatInstant(c.getValue().getEndDate())));
        resourcesColumn.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getResources()));
        impactColumn.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getImpact()));
        dependsOnColumn.setCellValueFactory(c -> new SimpleStringProperty(dependencyNames(c.getValue())));

        durationUnitCombo.setItems(FXCollections.observableArrayList(DurationUnit.values()));
        durationField.setTextFormatter(digitsOnly());
        resourcesField.setTextFormatter(digitsOnly());
        impactField.setTextFormatter(digitsOnly());

        dependenciesList.setPlaceholder(new Label("No other activities to depend on"));
        dependenciesList.setCellFactory(CheckBoxListCell.forListView(
                item -> dependencyChecks.computeIfAbsent(item.getId(), id -> new SimpleBooleanProperty()),
                new StringConverter<>() {
                    @Override
                    public String toString(ActivityDto item) {
                        return item == null ? "" : item.getName();
                    }

                    @Override
                    public ActivityDto fromString(String string) {
                        return null;
                    }
                }));

        table.setItems(context.getActivities());
        table.getSelectionModel().selectedItemProperty().addListener((obs, old, now) -> {
            if (now != null) {
                populate(now);
            }
        });
        context.getActivities().addListener((ListChangeListener<ActivityDto>) c -> refresh());
        refresh();
    }

    private static TextFormatter<String> digitsOnly() {
        return new TextFormatter<>(change -> change.getControlNewText().matches("\\d*") ? change : null);
    }

    private void refresh() {
        UUID keep = editingId;
        ActivityDto match = keep == null ? null
                : context.getActivities().stream().filter(a -> a.getId().equals(keep)).findFirst().orElse(null);
        if (match != null) {
            table.getSelectionModel().select(match);
            populate(match);
        } else {
            clearForm();
        }
    }

    private String dependencyNames(ActivityDto activity) {
        return context.getActivities().stream().filter(a -> activity.getDependencies().contains(a.getId()))
                .map(ActivityDto::getName).collect(Collectors.joining(", "));
    }

    private void populate(ActivityDto dto) {
        editingId = dto.getId();
        nameField.setText(dto.getName());
        descriptionArea.setText(dto.getDescription());
        durationField.setText(Long.toString(dto.getDuration()));
        durationUnitCombo.setValue(dto.getDurationUnit() == null ? DurationUnit.DAYS : dto.getDurationUnit());
        resourcesField.setText(Integer.toString(dto.getResources()));
        impactField.setText(Integer.toString(dto.getImpact()));
        statusField.setText(dto.getStatus());
        categoryField.setText(dto.getCategory());
        setInstant(startPicker, startTimeField, dto.getStartDate());
        setInstant(endPicker, endTimeField, dto.getEndDate());
        loadTags(dto.getTags());
        loadMetadata(dto.getMetadata());
        loadDependencyChoices(dto.getId(), dto.getDependencies());
        deleteButton.setDisable(false);
    }

    private void clearForm() {
        editingId = null;
        table.getSelectionModel().clearSelection();
        nameField.clear();
        descriptionArea.clear();
        durationField.setText("1");
        durationUnitCombo.setValue(DurationUnit.DAYS);
        resourcesField.setText("1");
        impactField.setText("1");
        statusField.clear();
        categoryField.clear();
        setInstant(startPicker, startTimeField, null);
        setInstant(endPicker, endTimeField, null);
        loadTags(Set.of());
        loadMetadata(Map.of());
        loadDependencyChoices(null, Set.of());
        deleteButton.setDisable(true);
    }

    /**
     * Offers every activity of the project except this one and those that (transitively) depend on it, since
     * choosing them would create a cycle.
     */
    private void loadDependencyChoices(UUID currentId, Set<UUID> selected) {
        Set<UUID> excluded = currentId == null ? Set.of() : transitiveDependents(currentId);
        List<ActivityDto> choices = context.getActivities().stream()
                .filter(a -> !a.getId().equals(currentId) && !excluded.contains(a.getId())).toList();
        dependencyChecks.clear();
        choices.forEach(a -> dependencyChecks.put(a.getId(), new SimpleBooleanProperty(selected.contains(a.getId()))));
        dependenciesList.getItems().setAll(choices);
        dependenciesList.refresh();
        dependenciesHint.setText(excluded.isEmpty() ? DEPENDENCY_HINT
                : DEPENDENCY_HINT + " " + excluded.size()
                        + " activity(ies) depending on this one are hidden to avoid cycles.");
    }

    private Set<UUID> transitiveDependents(UUID id) {
        Set<UUID> result = new HashSet<>();
        Deque<UUID> queue = new ArrayDeque<>(List.of(id));
        while (!queue.isEmpty()) {
            UUID current = queue.poll();
            for (ActivityDto a : context.getActivities()) {
                if (a.getDependencies().contains(current) && result.add(a.getId())) {
                    queue.add(a.getId());
                }
            }
        }
        return result;
    }

    private void loadMetadata(Map<String, String> metadata) {
        metadataRows.getChildren().clear();
        metadata.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(e -> addMetadataRow(e.getKey(), e.getValue()));
    }

    private void addMetadataRow(String key, String value) {
        TextField keyField = new TextField(key);
        keyField.setPromptText("key");
        keyField.setPrefColumnCount(8);
        TextField valueField = new TextField(value);
        valueField.setPromptText("value");
        HBox.setHgrow(valueField, Priority.ALWAYS);
        Button remove = new Button("✕");
        HBox row = new HBox(4, keyField, valueField, remove);
        remove.setOnAction(e -> metadataRows.getChildren().remove(row));
        metadataRows.getChildren().add(row);
    }

    private static String formatInstant(Instant instant) {
        return instant == null ? "" : DATE_TIME.format(LocalDateTime.ofInstant(instant, ZoneId.systemDefault()));
    }

    private static void setInstant(DatePicker picker, TextField timeField, Instant instant) {
        if (instant == null) {
            picker.setValue(null);
            timeField.setText("00:00");
            return;
        }
        LocalDateTime local = LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
        picker.setValue(local.toLocalDate());
        timeField.setText(TIME.format(local));
    }

    private static Instant readInstant(DatePicker picker, TextField timeField, String label) {
        LocalDate date = picker.getValue();
        if (date == null) {
            return null;
        }
        String text = timeField.getText() == null ? "" : timeField.getText().trim();
        try {
            LocalTime time = text.isEmpty() ? LocalTime.MIDNIGHT : LocalTime.parse(text);
            return date.atTime(time).atZone(ZoneId.systemDefault()).toInstant();
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(label + " time must be HH:mm, e.g. 14:30");
        }
    }

    private void loadTags(Set<String> tags) {
        editTags.clear();
        editTags.addAll(tags);
        tagField.clear();
        redrawTags();
    }

    private void redrawTags() {
        tagsPane.getChildren().clear();
        for (String tag : editTags) {
            Label text = new Label(tag);
            Button remove = new Button("✕");
            remove.setStyle("-fx-background-color: transparent; -fx-padding: 0 0 0 2; -fx-text-fill: #3367d6;");
            HBox badge = new HBox(2, text, remove);
            badge.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            badge.setStyle("-fx-background-color: #e8f0fe; -fx-background-radius: 10; -fx-padding: 2 6 2 8;");
            remove.setOnAction(e -> {
                editTags.remove(tag);
                redrawTags();
            });
            tagsPane.getChildren().add(badge);
        }
    }

    @FXML
    private void onAddTag() {
        String tag = tagField.getText() == null ? "" : tagField.getText().trim();
        if (!tag.isEmpty()) {
            editTags.add(tag);
            tagField.clear();
            redrawTags();
        }
        tagField.requestFocus();
    }

    @FXML
    private void onAddMetadata() {
        addMetadataRow("", "");
        HBox last = (HBox) metadataRows.getChildren().get(metadataRows.getChildren().size() - 1);
        last.getChildren().get(0).requestFocus();
    }

    private Map<String, String> collectMetadata() {
        Map<String, String> result = new LinkedHashMap<>();
        for (var node : metadataRows.getChildren()) {
            HBox row = (HBox) node;
            String key = ((TextField) row.getChildren().get(0)).getText().trim();
            String value = ((TextField) row.getChildren().get(1)).getText().trim();
            if (key.isEmpty() && value.isEmpty()) {
                continue;
            }
            if (key.isEmpty()) {
                throw new IllegalArgumentException("Metadata value '" + value + "' needs a key.");
            }
            if (result.put(key, value) != null) {
                throw new IllegalArgumentException("Duplicate metadata key: " + key);
            }
        }
        return result;
    }

    @FXML
    private void onNew() {
        clearForm();
        nameField.requestFocus();
    }

    @FXML
    private void onSave() {
        if (context.getCurrentProject() == null) {
            return;
        }
        try {
            ActivityDto dto = new ActivityDto();
            if (editingId != null) {
                ActivityDto existing = context.getActivities().stream().filter(a -> a.getId().equals(editingId))
                        .findFirst().orElse(null);
                if (existing != null) {
                    dto.setId(existing.getId());
                    dto.setCreatedAt(existing.getCreatedAt());
                }
            }
            dto.setName(nameField.getText() == null ? "" : nameField.getText().trim());
            dto.setDescription(descriptionArea.getText());
            dto.setDuration(Long.parseLong(durationField.getText().trim()));
            dto.setDurationUnit(durationUnitCombo.getValue() == null ? DurationUnit.DAYS : durationUnitCombo.getValue());
            dto.setResources(Integer.parseInt(resourcesField.getText().trim()));
            dto.setImpact(Integer.parseInt(impactField.getText().trim()));
            dto.setStatus(blankToNull(statusField.getText()));
            dto.setCategory(blankToNull(categoryField.getText()));
            dto.setStartDate(readInstant(startPicker, startTimeField, "Start"));
            dto.setEndDate(readInstant(endPicker, endTimeField, "End"));
            dto.setTags(new LinkedHashSet<>(editTags));
            dto.setMetadata(collectMetadata());
            dto.setDependencies(dependenciesList.getItems().stream()
                    .filter(a -> dependencyChecks.containsKey(a.getId()) && dependencyChecks.get(a.getId()).get())
                    .map(ActivityDto::getId).collect(Collectors.toCollection(LinkedHashSet::new)));
            if (editingId != null) {
                Set<UUID> shown = dependenciesList.getItems().stream().map(ActivityDto::getId)
                        .collect(Collectors.toSet());
                context.getAllActivities().stream().filter(a -> a.getId().equals(editingId)).findFirst()
                        .ifPresent(old -> old.getDependencies().stream()
                                .filter(d -> !shown.contains(d) && context.getAllActivities().stream()
                                        .anyMatch(a -> a.getId().equals(d) && !context.getActivities().contains(a)))
                                .forEach(d -> dto.getDependencies().add(d)));
            }
            ActivityDto saved = context.saveActivity(dto);
            editingId = saved.getId();
            refresh();
        } catch (NumberFormatException e) {
            error("Duration, resources and impact must be whole numbers.");
        } catch (IllegalArgumentException e) {
            error(e.getMessage());
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    @FXML
    private void onDelete() {
        ActivityDto selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        List<String> dependents = context.dependentNames(selected.getId());
        String message = dependents.isEmpty() ? "Delete activity '" + selected.getName() + "'?"
                : "Activity '" + selected.getName() + "' has a dependency tree: " + dependents.size()
                        + " activities depend on it (directly or indirectly):\n  - "
                        + String.join("\n  - ", dependents)
                        + "\n\nDeleting it removes it from their dependencies. Continue?";
        Alert alert = new Alert(dependents.isEmpty() ? AlertType.CONFIRMATION : AlertType.WARNING, message,
                ButtonType.OK, ButtonType.CANCEL);
        alert.setHeaderText(null);
        alert.showAndWait().filter(b -> b == ButtonType.OK).ifPresent(b -> {
            editingId = null;
            context.deleteActivity(selected.getId());
        });
    }

    @FXML
    private void onExport() {
        if (context.getCurrentProject() == null) {
            return;
        }
        FileChooser chooser = jsonChooser("Export activities");
        chooser.setInitialFileName(context.getCurrentProject().getName().replaceAll("[\\\\/:*?\"<>|]", "_")
                + "-activities.json");
        File file = chooser.showSaveDialog(table.getScene().getWindow());
        if (file == null) {
            return;
        }
        try {
            context.exportActivities(file.toPath());
            info("Exported " + context.getActivities().size() + " activities to " + file);
        } catch (IOException e) {
            error("Cannot write " + file + ": " + e.getMessage());
        } catch (IllegalArgumentException e) {
            error(e.getMessage());
        }
    }

    @FXML
    private void onImport() {
        if (context.getCurrentProject() == null) {
            return;
        }
        File file = jsonChooser("Import activities").showOpenDialog(table.getScene().getWindow());
        if (file == null) {
            return;
        }
        try {
            int count = context.importActivities(file.toPath());
            info("Imported " + count + " activities into '" + context.getCurrentProject().getName() + "'.");
        } catch (IOException e) {
            error("Cannot read " + file + ": " + e.getMessage());
        } catch (IllegalArgumentException e) {
            error(e.getMessage());
        }
    }

    private static FileChooser jsonChooser(String title) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("JSON files", "*.json"),
                new FileChooser.ExtensionFilter("All files", "*.*"));
        return chooser;
    }

    private void info(String message) {
        Alert alert = new Alert(AlertType.INFORMATION, message);
        alert.setHeaderText(null);
        alert.showAndWait();
    }

    private void error(String message) {
        Alert alert = new Alert(AlertType.ERROR, message);
        alert.setHeaderText(null);
        alert.showAndWait();
    }
}