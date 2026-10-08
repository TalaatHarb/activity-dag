package net.talaatharb.activitydag.ui;

import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;

import javafx.collections.ListChangeListener;
import javafx.collections.ObservableSet;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import net.talaatharb.activitydag.dto.ActivityDto;

/** Two scrollable rows of check boxes (statuses, categories) controlling which activities are shown. */
public class FilterBar extends VBox {
    private final HBox statusRow = new HBox(10);
    private final HBox categoryRow = new HBox(10);

    public FilterBar() {
        super(4);
        setPadding(new Insets(6, 8, 6, 8));
        getChildren().addAll(scroller("Status:", statusRow), scroller("Category:", categoryRow));
    }

    private static HBox scroller(String title, HBox row) {
        row.setAlignment(Pos.CENTER_LEFT);
        ScrollPane pane = new ScrollPane(row);
        pane.setFitToHeight(true);
        pane.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        pane.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        pane.setPrefViewportHeight(34);
        pane.setMinHeight(52);
        HBox.setHgrow(pane, javafx.scene.layout.Priority.ALWAYS);
        Label label = new Label(title);
        label.setMinWidth(70);
        HBox box = new HBox(8, label, pane);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    public void init(ProjectContext context) {
        Runnable rebuild = () -> {
            rebuild(statusRow, context, ProjectContext::key, ActivityDto::getStatus, context.getHiddenStatuses());
            rebuild(categoryRow, context, ProjectContext::key, ActivityDto::getCategory, context.getHiddenCategories());
        };
        context.getAllActivities().addListener((ListChangeListener<ActivityDto>) c -> rebuild.run());
        rebuild.run();
    }

    private static void rebuild(HBox row, ProjectContext context, Function<String, String> key,
            Function<ActivityDto, String> getter, ObservableSet<String> hidden) {
        Set<String> values = new TreeSet<>();
        values.add("");
        values.addAll(hidden);
        context.getAllActivities().forEach(a -> values.add(key.apply(getter.apply(a))));
        row.getChildren().clear();
        for (String value : values) {
            CheckBox box = new CheckBox(value.isEmpty() ? "(none)" : value);
            box.setSelected(!hidden.contains(value));
            box.selectedProperty().addListener((obs, old, now) -> {
                if (now) {
                    hidden.remove(value);
                } else {
                    hidden.add(value);
                }
            });
            row.getChildren().add(box);
        }
    }
}
