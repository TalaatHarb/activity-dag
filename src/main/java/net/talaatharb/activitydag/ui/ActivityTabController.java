package net.talaatharb.activitydag.ui;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.google.inject.Inject;

import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DatePicker;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import net.talaatharb.activitydag.dto.ActivityDto;

/** Define, view, edit and delete activities, including their dependencies. */
public class ActivityTabController {
    private final ProjectContext context;
    private UUID editingId;

    @FXML private TableView<ActivityDto> table;
    @FXML private TableColumn<ActivityDto, String> nameColumn;
    @FXML private TableColumn<ActivityDto, Number> durationColumn;
    @FXML private TableColumn<ActivityDto, LocalDate> startColumn;
    @FXML private TableColumn<ActivityDto, LocalDate> endColumn;
    @FXML private TableColumn<ActivityDto, Number> resourcesColumn;
    @FXML private TableColumn<ActivityDto, String> dependsOnColumn;
    @FXML private TextField nameField;
    @FXML private TextArea descriptionArea;
    @FXML private TextField durationField;
    @FXML private TextField resourcesField;
    @FXML private DatePicker startPicker;
    @FXML private DatePicker endPicker;
    @FXML private ListView<ActivityDto> dependenciesList;
    @FXML private TextArea metadataArea;
    @FXML private Button deleteButton;

    @Inject
    public ActivityTabController(ProjectContext context) {
        this.context = context;
    }

    @FXML
    private void initialize() {
        nameColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getName()));
        durationColumn.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getDuration()));
        startColumn.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getStartDate()));
        endColumn.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getEndDate()));
        resourcesColumn.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getResources()));
        dependsOnColumn.setCellValueFactory(c -> new SimpleStringProperty(dependencyNames(c.getValue())));

        dependenciesList.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        dependenciesList.setCellFactory(l -> new ListCell<>() {
            @Override
            protected void updateItem(ActivityDto item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getName());
            }
        });

        table.setItems(context.getActivities());
        table.getSelectionModel().selectedItemProperty().addListener((obs, old, now) -> {
            if (now != null) {
                populate(now);
            }
        });
        context.getActivities().addListener((ListChangeListener<ActivityDto>) c -> refresh());
        refresh();
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
        resourcesField.setText(Integer.toString(dto.getResources()));
        startPicker.setValue(dto.getStartDate());
        endPicker.setValue(dto.getEndDate());
        metadataArea.setText(dto.getMetadata().entrySet().stream().map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("\n")));
        loadDependencyChoices(dto.getId(), dto.getDependencies());
        deleteButton.setDisable(false);
    }

    private void clearForm() {
        editingId = null;
        table.getSelectionModel().clearSelection();
        nameField.clear();
        descriptionArea.clear();
        durationField.setText("1");
        resourcesField.setText("1");
        startPicker.setValue(null);
        endPicker.setValue(null);
        metadataArea.clear();
        loadDependencyChoices(null, Set.of());
        deleteButton.setDisable(true);
    }

    private void loadDependencyChoices(UUID excludeId, Set<UUID> selected) {
        List<ActivityDto> choices = context.getActivities().stream().filter(a -> !a.getId().equals(excludeId))
                .toList();
        dependenciesList.getItems().setAll(choices);
        dependenciesList.getSelectionModel().clearSelection();
        for (int i = 0; i < choices.size(); i++) {
            if (selected.contains(choices.get(i).getId())) {
                dependenciesList.getSelectionModel().select(i);
            }
        }
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
            dto.setResources(Integer.parseInt(resourcesField.getText().trim()));
            dto.setStartDate(startPicker.getValue());
            dto.setEndDate(endPicker.getValue());
            dto.setMetadata(parseMetadata(metadataArea.getText()));
            dto.setDependencies(dependenciesList.getSelectionModel().getSelectedItems().stream()
                    .map(ActivityDto::getId).collect(Collectors.toCollection(LinkedHashSet::new)));
            ActivityDto saved = context.saveActivity(dto);
            editingId = saved.getId();
            refresh();
        } catch (NumberFormatException e) {
            error("Duration and resources must be whole numbers.");
        } catch (IllegalArgumentException e) {
            error(e.getMessage());
        }
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

    static Map<String, String> parseMetadata(String text) {
        Map<String, String> result = new HashMap<>();
        if (text == null) {
            return result;
        }
        for (String line : text.split("\\R")) {
            if (line.isBlank()) {
                continue;
            }
            int idx = line.indexOf('=');
            if (idx <= 0) {
                throw new IllegalArgumentException("Metadata lines must look like key=value: " + line);
            }
            result.put(line.substring(0, idx).trim(), line.substring(idx + 1).trim());
        }
        return result;
    }

    private void error(String message) {
        Alert alert = new Alert(AlertType.ERROR, message);
        alert.setHeaderText(null);
        alert.showAndWait();
    }
}
