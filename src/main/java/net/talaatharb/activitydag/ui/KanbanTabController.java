package net.talaatharb.activitydag.ui;

import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import com.google.inject.Inject;

import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DataFormat;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import net.talaatharb.activitydag.dto.ActivityDto;

/** Kanban board of the (filtered) activities; columns are the values of the status or category field. */
public class KanbanTabController {
    private static final double COLUMN_W = 220;
    private static final String COLUMN_STYLE = "-fx-background-color: #f1f3f4; -fx-background-radius: 6;";
    private static final String CARD_STYLE =
            "-fx-background-color: white; -fx-border-color: #3367d6; -fx-border-radius: 6; -fx-background-radius: 6;";

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
        board.getChildren().add(column(""));
        for (String v : values) {
            if (!hidden.contains(v)) {
                board.getChildren().add(column(v));
            }
        }
    }

    private VBox column(String columnValue) {
        VBox column = new VBox(6);
        column.setPadding(new Insets(6));
        column.setPrefWidth(COLUMN_W);
        column.setMinWidth(COLUMN_W);
        column.setStyle(COLUMN_STYLE);
        Label header = new Label(columnValue.isEmpty() ? "(none)" : columnValue);
        header.setStyle("-fx-font-weight: bold;");
        column.getChildren().add(header);
        for (ActivityDto a : context.getActivities()) {
            if (value(a).equals(columnValue)) {
                column.getChildren().add(card(a));
            }
        }
        column.setOnDragOver(e -> {
            if (e.getDragboard().hasContent(ACTIVITY_ID)) {
                e.acceptTransferModes(TransferMode.MOVE);
            }
            e.consume();
        });
        column.setOnDragDropped(e -> {
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
        title.setStyle("-fx-font-weight: bold;");
        VBox card = new VBox(4, title);
        if (!a.getTags().isEmpty()) {
            FlowPane tags = new FlowPane(4, 4);
            a.getTags().stream().sorted(String.CASE_INSENSITIVE_ORDER).forEach(t -> {
                Label badge = new Label(t);
                badge.setStyle("-fx-background-color: #e8f0fe; -fx-text-fill: #1a4db3; "
                        + "-fx-background-radius: 10; -fx-padding: 1 8 1 8; -fx-font-size: 0.85em;");
                tags.getChildren().add(badge);
            });
            card.getChildren().add(tags);
        }
        String other = ProjectContext.key(byStatus() ? a.getCategory() : a.getStatus());
        if (!other.isEmpty()) {
            Label field = new Label((byStatus() ? "Category: " : "Status: ") + other);
            field.setStyle("-fx-text-fill: dimgray; -fx-font-size: 0.9em;");
            card.getChildren().add(field);
        }
        card.setPadding(new Insets(6));
        card.setStyle(CARD_STYLE);
        card.setOnDragDetected(e -> {
            Dragboard db = card.startDragAndDrop(TransferMode.MOVE);
            ClipboardContent content = new ClipboardContent();
            content.put(ACTIVITY_ID, a.getId().toString());
            db.setContent(content);
            e.consume();
        });
        return card;
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
            return false;
        }
        return true;
    }
}
