package net.talaatharb.activitydag.ui;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.google.inject.Inject;

import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import net.talaatharb.activitydag.dto.ActivityDto;
import net.talaatharb.activitydag.ui.graph.GraphLayout;
import net.talaatharb.activitydag.ui.graph.GraphLayout.Cell;

/** Draws the activities of the current project as a DAG. */
public class GraphTabController {
    private static final double NODE_W = 170;
    private static final double NODE_H = 56;
    private static final double GAP_X = 80;
    private static final double GAP_Y = 30;
    private static final double MARGIN = 30;

    private final ProjectContext context;

    @FXML private Pane graphPane;

    @Inject
    public GraphTabController(ProjectContext context) {
        this.context = context;
    }

    @FXML
    private void initialize() {
        context.getActivities().addListener((ListChangeListener<ActivityDto>) c -> draw());
        draw();
    }

    private void draw() {
        graphPane.getChildren().clear();
        List<ActivityDto> activities = List.copyOf(context.getActivities());
        Map<UUID, Cell> cells = GraphLayout.compute(activities);
        double maxX = 0;
        double maxY = 0;
        for (ActivityDto a : activities) {
            Cell cell = cells.get(a.getId());
            for (UUID dep : a.getDependencies()) {
                Cell from = cells.get(dep);
                if (from != null) {
                    drawEdge(from, cell);
                }
            }
        }
        for (ActivityDto a : activities) {
            Cell cell = cells.get(a.getId());
            double x = x(cell);
            double y = y(cell);
            Rectangle box = new Rectangle(x, y, NODE_W, NODE_H);
            box.setArcWidth(12);
            box.setArcHeight(12);
            box.setFill(Color.web("#e8f0fe"));
            box.setStroke(Color.web("#3367d6"));
            Text title = new Text(x + 8, y + 22, a.getName());
            title.setWrappingWidth(NODE_W - 16);
            Text detail = new Text(x + 8, y + 42, a.durationText() + ", " + a.getResources() + " res");
            detail.setFill(Color.DIMGRAY);
            graphPane.getChildren().addAll(box, title, detail);
            maxX = Math.max(maxX, x + NODE_W + MARGIN);
            maxY = Math.max(maxY, y + NODE_H + MARGIN);
        }
        graphPane.setMinSize(maxX, maxY);
        graphPane.setPrefSize(maxX, maxY);
    }

    private void drawEdge(Cell from, Cell to) {
        double x1 = x(from) + NODE_W;
        double y1 = y(from) + NODE_H / 2;
        double x2 = x(to);
        double y2 = y(to) + NODE_H / 2;
        Line line = new Line(x1, y1, x2, y2);
        line.setStroke(Color.GRAY);
        double angle = Math.atan2(y2 - y1, x2 - x1);
        double size = 9;
        Polygon arrow = new Polygon(x2, y2,
                x2 - size * Math.cos(angle - Math.PI / 7), y2 - size * Math.sin(angle - Math.PI / 7),
                x2 - size * Math.cos(angle + Math.PI / 7), y2 - size * Math.sin(angle + Math.PI / 7));
        arrow.setFill(Color.GRAY);
        graphPane.getChildren().addAll(line, arrow);
    }

    private static double x(Cell c) {
        return MARGIN + c.layer() * (NODE_W + GAP_X);
    }

    private static double y(Cell c) {
        return MARGIN + c.row() * (NODE_H + GAP_Y);
    }
}
