package net.talaatharb.activitydag.ui.graph;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import eu.dariolucia.jfx.timeline.Timeline;
import eu.dariolucia.jfx.timeline.model.IRenderingContext;
import eu.dariolucia.jfx.timeline.model.TaskItem;
import eu.dariolucia.jfx.timeline.model.TaskLine;
import eu.dariolucia.jfx.timeline.model.TimeInterval;
import eu.dariolucia.jfx.timeline.model.TimePoint;
import eu.dariolucia.jfx.timeline.model.TimePointType;
import eu.dariolucia.jfx.timeline.model.TimeTooltip;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import net.talaatharb.activitydag.model.DurationUnit;
import net.talaatharb.activitydag.planning.PlanResult;
import net.talaatharb.activitydag.planning.PlannedActivity;

/** Read-only Timeline view of the planned schedule, including critical work, slack and milestones. */
public class GanttChart extends BorderPane {
    private static final Color CRITICAL = Color.web("#c62828");
    private static final Color STANDARD = Color.web("#2563eb");
    private static final Color SLACK = Color.web("#e2e8f0");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final Timeline timeline = new Timeline() {
        @Override
        public TaskItem getTaskItemAt(int index) {
            // 0.9.1's selection model requests index -1 when clearing selection.
            return index < 0 || index >= getTaskItemCount() ? null : super.getTaskItemAt(index);
        }
    };
    private final Label empty = new Label("Choose a strategy and click Plan to see the schedule.");
    private final Button fitButton = new Button("Fit schedule");
    private Instant rangeStart;
    private long rangeSeconds;

    public GanttChart() {
        timeline.setTaskPanelWidth(220);
        timeline.setHeaderBackground(Color.web("#eef2f6"));
        timeline.setPanelBackground(Color.web("#f8fafc"));
        timeline.setHeaderForegroundColor(Color.web("#334155"));
        timeline.setPanelForegroundColor(Color.web("#334155"));
        timeline.setEnableAlternateColorLines(true);
        timeline.setEnableVerticalLines(true);
        timeline.setEnableZoomMouseScroll(true);
        timeline.setHorizontalScrollbarVisible(true);
        timeline.setVerticalScrollbarVisible(true);
        timeline.setTextPadding(6);
        timeline.setMinSize(0, 0);

        fitButton.setOnAction(e -> fitSchedule());
        HBox toolbar = new HBox(12, fitButton, legend("Critical", CRITICAL),
                legend("Activity", STANDARD), legend("Slack", SLACK),
                new Label("Scroll to navigate; Ctrl + scroll to zoom."));
        toolbar.setPadding(new Insets(6));
        toolbar.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        setTop(toolbar);
        setMinSize(0, 0);
        show(null);
    }

    private static Label legend(String text, Color color) {
        Label label = new Label(text);
        javafx.scene.shape.Rectangle swatch = new javafx.scene.shape.Rectangle(10, 10, color);
        label.setGraphic(swatch);
        return label;
    }

    public void show(PlanResult result) {
        timeline.getSelectionModel().clearSelection();
        timeline.getItems().clear();
        fitButton.setDisable(result == null || result.activities().isEmpty());
        if (result == null || result.activities().isEmpty()) {
            rangeStart = null;
            empty.setText(result == null ? "Choose a strategy and click Plan to see the schedule."
                    : "No activities to schedule.");
            setCenter(empty);
            return;
        }

        List<TaskLine> lines = new ArrayList<>();
        Instant first = null;
        Instant last = null;
        for (PlannedActivity activity : result.activities()) {
            // Timeline's axis is UTC. Encode the planner's calendar times as UTC to match the result table,
            // without introducing a system-zone/DST shift into its continuous-calendar schedule.
            Instant start = activity.start().toInstant(ZoneOffset.UTC);
            Instant end = activity.end().toInstant(ZoneOffset.UTC);
            Instant slackEnd = end.plusSeconds(activity.slack() * 60L);
            first = first == null || start.isBefore(first) ? start : first;
            last = last == null || slackEnd.isAfter(last) ? slackEnd : last;
            lines.add(taskLine(activity, start, end, slackEnd));
        }
        long span = Math.max(60, Duration.between(first, last).getSeconds());
        long padding = Math.max(60, span / 20);
        rangeStart = first.minusSeconds(padding);
        rangeSeconds = span + 2 * padding;
        timeline.setMinTime(rangeStart);
        timeline.setMaxTime(rangeStart.plusSeconds(rangeSeconds));
        fitSchedule();
        timeline.getItems().setAll(lines);
        setCenter(timeline);
    }

    private static TaskLine taskLine(PlannedActivity activity, Instant start, Instant end, Instant slackEnd) {
        String details = activity.name() + "\nStart: " + TIME_FORMAT.format(activity.start())
                + "\nEnd: " + TIME_FORMAT.format(activity.end())
                + "\nResources: " + activity.resources()
                + "\nImpact: " + String.format(Locale.ROOT, "%.1f%%", activity.impactPercent())
                + "\nSlack: " + DurationUnit.format(activity.slack())
                + (activity.critical() ? "\nCritical path" : "");
        TaskLine line = new TaskLine(activity.name(), details);
        TaskItem item = new TaskItem(activity.name(), start, Duration.between(start, end).getSeconds());
        Color color = activity.critical() ? CRITICAL : STANDARD;
        item.setTaskBackground(color);
        item.setTaskTextColor(Color.WHITE);
        item.setTooltip(new TimeTooltip(details));
        item.setUserData(activity.id());
        if (start.equals(end)) {
            TimePoint milestone = new TimePoint("", start, TimePointType.CIRCLE) {
                @Override
                protected void render(GraphicsContext gc, IRenderingContext rc, int x, int y,
                        int width, int height) {
                    // 0.9.1 clips time points to their parent duration, which is zero for a milestone.
                    super.render(gc, rc, x, y, Math.max(8, width), height);
                }
            };
            milestone.setColor(color);
            milestone.setTooltip(new TimeTooltip(details + "\nMilestone (zero duration)"));
            item.getTimePoints().add(milestone);
        }
        line.getItems().add(item);
        if (activity.slack() > 0) {
            TimeInterval slack = new TimeInterval(end, slackEnd);
            slack.setColor(SLACK);
            line.getIntervals().add(slack);
        }
        return line;
    }

    private void fitSchedule() {
        if (rangeStart != null) {
            timeline.setViewPortDuration(rangeSeconds);
            timeline.setViewPortStart(rangeStart);
        }
    }
}
