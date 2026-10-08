package net.talaatharb.activitydag.ui;

import java.util.Set;
import java.util.Comparator;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.TreeSet;
import java.util.UUID;

import com.google.inject.Inject;

import javafx.collections.ListChangeListener;
import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DataFormat;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import net.talaatharb.activitydag.dto.ActivityDto;

/** Kanban board of the (filtered) activities; columns are the values of the status or category field. */
public class KanbanTabController {
    private static final double COLUMN_W = 290;
    private static final String[] COLUMN_ACCENTS = { "#2563eb", "#7c3aed", "#0f766e", "#b45309", "#be185d" };
    private static final PseudoClass DROP_TARGET = PseudoClass.getPseudoClass("drop-target");
    private static final DateTimeFormatter START_FORMAT =
            DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm").withZone(ZoneId.systemDefault());
    private static final Comparator<ActivityDto> START_ORDER =
            Comparator.comparing(ActivityDto::getStartDate, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(ActivityDto::getName, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(ActivityDto::getId);

    private final ProjectContext context;

    @FXML private FilterBar filterBar;
    @FXML private HBox board;
    @FXML private ScrollPane scroll;
    @FXML private RadioButton statusRadio;
    @FXML private RadioButton categoryRadio;

    @Inject
    public KanbanTabController(ProjectContext context) {
        this.context = context;
    }

    @FXML
    private void initialize() {
        filterBar.init(context);
        context.getActivities().addListener((ListChangeListener<ActivityDto>) c -> draw());
        context.getAllActivities().addListener((ListChangeListener<ActivityDto>) c -> draw());
        statusRadio.selectedProperty().addListener((obs, old, now) -> draw());
        draw();
    }

    private boolean byStatus() {
        return statusRadio.isSelected();
    }

    private String value(ActivityDto a) {
        return ProjectContext.key(byStatus() ? a.getStatus() : a.getCategory());
    }

    private void draw() {
        board.getChildren().clear();
        Set<String> hidden = byStatus() ? context.getHiddenStatuses() : context.getHiddenCategories();
        Set<String> values = new TreeSet<>();
        context.getAllActivities().forEach(a -> values.add(value(a)));
        values.remove("");
        if (!hidden.contains("")) {
            board.getChildren().add(column(""));
        }
        for (String v : values) {
            if (!hidden.contains(v)) {
                board.getChildren().add(column(v));
            }
        }
    }

    private VBox column(String columnValue) {
        VBox column = new VBox(12);
        column.setPadding(new Insets(12));
        column.setPrefWidth(COLUMN_W);
        column.setMinWidth(COLUMN_W);
        column.setMaxWidth(COLUMN_W);
        column.getStyleClass().add("kanban-column");
        String accent = columnValue.isEmpty() ? "#64748b"
                : COLUMN_ACCENTS[Math.floorMod(columnValue.hashCode(), COLUMN_ACCENTS.length)];
        column.setStyle("-kanban-accent: " + accent + ";");
        Label header = new Label(columnValue.isEmpty() ? "Unassigned" : columnValue);
        header.setWrapText(true);
        header.getStyleClass().add("kanban-heading");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        long count = context.getActivities().stream().filter(a -> value(a).equals(columnValue)).count();
        Label counter = new Label(Long.toString(count));
        counter.setMinWidth(Region.USE_PREF_SIZE);
        counter.getStyleClass().add("kanban-count");
        HBox heading = new HBox(8, header, spacer, counter);
        heading.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        heading.getStyleClass().add("kanban-column-header");
        column.getChildren().add(heading);
        context.getActivities().stream().filter(a -> value(a).equals(columnValue)).sorted(START_ORDER)
                .forEach(a -> column.getChildren().add(card(a)));
        if (count == 0) {
            Label hint = new Label("Drop an activity here");
            hint.getStyleClass().add("kanban-empty");
            hint.setMaxWidth(Double.MAX_VALUE);
            column.getChildren().add(hint);
        }
        column.setOnDragOver(e -> {
            if (e.getDragboard().hasContent(ACTIVITY_ID)) {
                e.acceptTransferModes(TransferMode.MOVE);
                column.pseudoClassStateChanged(DROP_TARGET, true);
            }
            e.consume();
        });
        column.setOnDragExited(e -> column.pseudoClassStateChanged(DROP_TARGET, false));
        column.setOnDragDropped(e -> {
            column.pseudoClassStateChanged(DROP_TARGET, false);
            Dragboard db = e.getDragboard();
            boolean done = false;
            if (db.hasContent(ACTIVITY_ID)) {
                UUID id = UUID.fromString((String) db.getContent(ACTIVITY_ID));
                done = move(id, columnValue);
            }
            e.setDropCompleted(done);
            e.consume();
        });
        return column;
    }

    private static final DataFormat ACTIVITY_ID = new DataFormat("application/x-activity-dag-id");

    private VBox card(ActivityDto a) {
        Label title = new Label(a.getName());
        title.setWrapText(true);
        title.setMaxWidth(Double.MAX_VALUE);
        title.getStyleClass().add("kanban-card-title");
        VBox card = new VBox(8, title);
        card.setUserData(a.getId());
        card.setMinHeight(Region.USE_PREF_SIZE);
        Label start = new Label("Start: " + (a.getStartDate() == null ? "Unscheduled"
                : START_FORMAT.format(a.getStartDate())));
        start.getStyleClass().add("kanban-start");
        card.getChildren().add(start);
        card.getChildren().addAll(
                field("Status", a.getStatus(), "kanban-status"),
                field("Category", a.getCategory(), "kanban-category"));
        FlowPane metrics = new FlowPane(6, 6);
        metrics.setMinHeight(Region.USE_PREF_SIZE);
        metrics.getChildren().addAll(badge("Impact: " + a.getImpact(), "kanban-metric"),
                badge("Resources: " + a.getResources(), "kanban-metric"));
        card.getChildren().add(metrics);
        if (!a.getTags().isEmpty()) {
            FlowPane tags = new FlowPane(4, 4);
            tags.setMinHeight(Region.USE_PREF_SIZE);
            a.getTags().stream().sorted(String.CASE_INSENSITIVE_ORDER).forEach(t -> {
                tags.getChildren().add(badge(t, "kanban-tag"));
            });
            Label tagsHeading = new Label("Tags");
            tagsHeading.getStyleClass().add("kanban-tags-heading");
            card.getChildren().addAll(tagsHeading, tags);
        }
        card.setPadding(new Insets(12));
        card.getStyleClass().add("kanban-card");
        card.setOnDragDetected(e -> {
            Dragboard db = card.startDragAndDrop(TransferMode.MOVE);
            ClipboardContent content = new ClipboardContent();
            content.put(ACTIVITY_ID, a.getId().toString());
            db.setContent(content);
            e.consume();
        });
        return card;
    }

    private static Label field(String name, String value, String styleClass) {
        String text = ProjectContext.key(value);
        return badge(name + ": " + (text.isEmpty() ? "Unassigned" : text), styleClass);
    }

    private static Label badge(String text, String styleClass) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.setMaxWidth(COLUMN_W - 50);
        label.getStyleClass().add(styleClass);
        return label;
    }

    private boolean move(UUID id, String target) {
        ActivityDto activity = context.getAllActivities().stream().filter(a -> a.getId().equals(id)).findFirst()
                .orElse(null);
        if (activity == null) {
            return false;
        }
        if (value(activity).equals(target)) {
            return true;
        }
        String newValue = target.isEmpty() ? null : target;
        if (byStatus()) {
            activity.setStatus(newValue);
        } else {
            activity.setCategory(newValue);
        }
        try {
            context.saveActivity(activity);
        } catch (IllegalArgumentException ex) {
            context.reloadActivities();
            Alert alert = new Alert(AlertType.ERROR, ex.getMessage());
            alert.setHeaderText("Could not move activity");
            alert.showAndWait();
            return false;
        }
        return true;
    }
}
