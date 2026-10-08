package net.talaatharb.activitydag.ui;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import com.google.inject.Inject;

import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import net.talaatharb.activitydag.model.DurationUnit;
import net.talaatharb.activitydag.planning.PlanResult;
import net.talaatharb.activitydag.ui.graph.GanttChart;
import net.talaatharb.activitydag.planning.PlannedActivity;

/** Apply a planning strategy to the current project's activities and optionally store the schedule. */
public class PlanningTabController {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final ProjectContext context;
    private PlanResult lastResult;

    @FXML private ComboBox<String> strategyCombo;
    @FXML private DatePicker startPicker;
    @FXML private Button applyButton;
    @FXML private Label summaryLabel;
    @FXML private GanttChart ganttChart;
    @FXML private TableView<PlannedActivity> resultTable;
    @FXML private TableColumn<PlannedActivity, String> nameColumn;
    @FXML private TableColumn<PlannedActivity, String> startColumn;
    @FXML private TableColumn<PlannedActivity, String> endColumn;
    @FXML private TableColumn<PlannedActivity, Number> resourcesColumn;
    @FXML private TableColumn<PlannedActivity, Number> impactColumn;
    @FXML private TableColumn<PlannedActivity, String> slackColumn;
    @FXML private TableColumn<PlannedActivity, String> criticalColumn;

    @Inject
    public PlanningTabController(ProjectContext context) {
        this.context = context;
    }

    @FXML private FilterBar filterBar;

    @FXML
    private void initialize() {
        filterBar.init(context);
        strategyCombo.setItems(FXCollections.observableArrayList(context.strategyNames()));
        strategyCombo.getSelectionModel().selectFirst();
        startPicker.setValue(LocalDate.now());
        nameColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().name()));
        startColumn.setCellValueFactory(c -> new SimpleStringProperty(TIME_FORMAT.format(c.getValue().start())));
        endColumn.setCellValueFactory(c -> new SimpleStringProperty(TIME_FORMAT.format(c.getValue().end())));
        resourcesColumn.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().resources()));
        impactColumn.setCellValueFactory(c -> new SimpleObjectProperty<>(Math.round(c.getValue().impactPercent() * 10) / 10.0));
        slackColumn.setCellValueFactory(c -> new SimpleStringProperty(DurationUnit.format(c.getValue().slack())));
        criticalColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().critical() ? "Yes" : ""));
        context.currentProjectProperty().addListener((obs, old, now) -> clear());
        context.getAllActivities().addListener((javafx.collections.ListChangeListener<Object>) c -> clear());
        context.getActivities().addListener((javafx.collections.ListChangeListener<Object>) c -> {
            if (lastResult != null) {
                onPlan();
            } else {
                clear();
            }
        });
        clear();
    }

    private void clear() {
        lastResult = null;
        resultTable.getItems().clear();
        ganttChart.show(null);
        applyButton.setDisable(true);
        summaryLabel.setText("");
    }

    @FXML
    private void onPlan() {
        try {
            lastResult = context.plan(strategyCombo.getValue(), startPicker.getValue());
            resultTable.getItems().setAll(lastResult.activities());
            ganttChart.show(lastResult);
            summaryLabel.setText("Total duration: " + DurationUnit.format(lastResult.totalMinutes()) + ", peak resources: "
                    + lastResult.peakResources());
            applyButton.setDisable(lastResult.activities().isEmpty());
        } catch (RuntimeException e) {
            Alert alert = new Alert(AlertType.ERROR, e.getMessage());
            alert.setHeaderText(null);
            alert.showAndWait();
        }
    }

    @FXML
    private void onApply() {
        if (lastResult != null) {
            PlanResult toApply = lastResult;
            context.applyPlan(toApply);
        }
    }
}
