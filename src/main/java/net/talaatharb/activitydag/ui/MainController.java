package net.talaatharb.activitydag.ui;

import com.google.inject.Inject;

import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextInputDialog;
import net.talaatharb.activitydag.dto.ProjectDto;

/** Project switcher shown above the tabs. */
public class MainController {
    private final ProjectContext context;

    @FXML private ComboBox<ProjectDto> projectCombo;

    @Inject
    public MainController(ProjectContext context) {
        this.context = context;
    }

    @FXML
    private void initialize() {
        projectCombo.setItems(context.getProjects());
        projectCombo.getSelectionModel().select(context.getCurrentProject());
        context.currentProjectProperty().addListener((obs, old, now) -> {
            if (now != null && !now.equals(projectCombo.getValue())) {
                projectCombo.getSelectionModel().select(now);
            }
        });
        projectCombo.setOnAction(e -> {
            ProjectDto selected = projectCombo.getValue();
            if (selected != null && selected != context.getCurrentProject()) {
                context.currentProjectProperty().set(selected);
            }
        });
    }

    @FXML
    private void onNewProject() {
        promptName("New project", "").ifPresent(name -> run(() -> context.createProject(name, "")));
    }

    @FXML
    private void onRenameProject() {
        ProjectDto current = context.getCurrentProject();
        if (current == null) {
            return;
        }
        promptName("Rename project", current.getName()).ifPresent(name -> run(() -> {
            current.setName(name);
            context.updateProject(current);
        }));
    }

    @FXML
    private void onDeleteProject() {
        ProjectDto current = context.getCurrentProject();
        if (current == null) {
            return;
        }
        Alert alert = new Alert(AlertType.CONFIRMATION,
                "Delete project '" + current.getName() + "' and all of its activities?", ButtonType.OK,
                ButtonType.CANCEL);
        alert.setHeaderText(null);
        alert.showAndWait().filter(b -> b == ButtonType.OK).ifPresent(b -> context.deleteCurrentProject());
    }

    private java.util.Optional<String> promptName(String title, String initial) {
        TextInputDialog dialog = new TextInputDialog(initial);
        dialog.setTitle(title);
        dialog.setHeaderText(null);
        dialog.setContentText("Name:");
        return dialog.showAndWait().map(String::trim).filter(s -> !s.isEmpty());
    }

    private void run(Runnable action) {
        try {
            action.run();
        } catch (IllegalArgumentException e) {
            new Alert(AlertType.ERROR, e.getMessage()).showAndWait();
        }
    }
}
