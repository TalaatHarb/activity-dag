package net.talaatharb.activitydag.ui.graph;

import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import net.talaatharb.activitydag.planning.PlanResult;
import net.talaatharb.activitydag.planning.PlannedActivity;

/** Gantt chart: one row per activity (in plan order), a bar per activity along a day axis; critical ones in red. */
public class GanttChart extends Pane {
    private static final double LABEL_W = 180;
    private static final double ROW_H = 28;
    private static final double BAR_H = 18;
    private static final double HEADER_H = 28;
    private static final double DAY_W = 26;
    private static final double PAD = 10;

    public void show(PlanResult result) {
        getChildren().clear();
        if (result == null || result.activities().isEmpty()) {
            setMinSize(0, 0);
            setPrefSize(0, 0);
            return;
        }
        int days = Math.max(1, result.totalDays());
        double width = PAD + LABEL_W + days * DAY_W + PAD;
        double height = HEADER_H + result.activities().size() * ROW_H + PAD;
        int rows = result.activities().size();

        for (int d = 0; d <= days; d++) {
            double x = PAD + LABEL_W + d * DAY_W;
            Line grid = new Line(x, HEADER_H - 6, x, HEADER_H + rows * ROW_H);
            grid.setStroke(Color.gray(0.88));
            getChildren().add(grid);
            if (d < days && (days <= 40 || d % 5 == 0)) {
                Text tick = new Text(x + 3, HEADER_H - 10, Integer.toString(d));
                tick.setFill(Color.DIMGRAY);
                getChildren().add(tick);
            }
        }
        Text axis = new Text(PAD, HEADER_H - 10, "Day");
        axis.setFill(Color.DIMGRAY);
        getChildren().add(axis);

        int row = 0;
        for (PlannedActivity p : result.activities()) {
            double y = HEADER_H + row * ROW_H;
            Text name = new Text(PAD, y + ROW_H / 2 + 4, p.name());
            double barX = PAD + LABEL_W + p.startOffset() * DAY_W;
            double barW = Math.max(3, (p.endOffset() - p.startOffset()) * DAY_W);
            Rectangle bar = new Rectangle(barX, y + (ROW_H - BAR_H) / 2, barW, BAR_H);
            bar.setArcWidth(6);
            bar.setArcHeight(6);
            bar.setFill(p.critical() ? Color.web("#d93025") : Color.web("#3367d6"));
            if (!p.critical() && p.slack() > 0) {
                Rectangle slack = new Rectangle(barX + barW, bar.getY() + BAR_H / 2 - 1, p.slack() * DAY_W, 2);
                slack.setFill(Color.gray(0.6));
                getChildren().add(slack);
            }
            getChildren().addAll(name, bar);
            row++;
        }
        setMinSize(width, height);
        setPrefSize(width, height);
    }
}
