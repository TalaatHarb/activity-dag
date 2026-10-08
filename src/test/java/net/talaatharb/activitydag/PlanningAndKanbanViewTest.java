package net.talaatharb.activitydag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.inject.Guice;
import com.calendarfx.view.DateControl;

import eu.dariolucia.jfx.timeline.Timeline;
import eu.dariolucia.jfx.timeline.model.TaskLine;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.CheckBox;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TableView;
import javafx.scene.Node;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import net.talaatharb.activitydag.config.AppModule;
import net.talaatharb.activitydag.dto.ActivityDto;
import net.talaatharb.activitydag.persistence.MapDbStorage;
import net.talaatharb.activitydag.planning.PlanResult;
import net.talaatharb.activitydag.planning.PlannedActivity;
import net.talaatharb.activitydag.ui.ProjectContext;
import net.talaatharb.activitydag.ui.graph.GanttChart;

class PlanningAndKanbanViewTest {
    @TempDir Path dir;
    private static boolean started;

    @BeforeAll
    static void startToolkit() throws Exception {
        // Linux CI runners without a display cannot initialise the JavaFX toolkit.
        assumeTrue(!System.getProperty("os.name").startsWith("Linux")
                || System.getenv("DISPLAY") != null);
        CountDownLatch ready = new CountDownLatch(1);
        Platform.startup(() -> {
            Platform.setImplicitExit(false);
            ready.countDown();
        });
        started = true;
        assertTrue(ready.await(20, TimeUnit.SECONDS));
    }

    @AfterAll
    static void stopToolkit() {
        if (started) {
            Platform.exit();
        }
    }

    private static void onFxThread(Runnable action) throws Exception {
        FutureTask<Void> task = new FutureTask<>(action, null);
        Platform.runLater(task);
        task.get(20, TimeUnit.SECONDS);
    }

    @Test
    void timelinePreservesTimesCriticalWorkSlackAndMilestones() throws Exception {
        onFxThread(() -> {
            GanttChart chart = new GanttChart();
            new Scene(chart, 1000, 350);
            LocalDateTime start = LocalDateTime.of(2026, 10, 9, 0, 0);
            PlannedActivity critical = new PlannedActivity(UUID.randomUUID(), "Critical", 0, 60,
                    start, start.plusHours(1), 2, 60, 0, true);
            PlannedActivity other = new PlannedActivity(UUID.randomUUID(), "Other", 0, 30,
                    start, start.plusMinutes(30), 1, 40, 30, false);
            PlannedActivity milestone = new PlannedActivity(UUID.randomUUID(), "Milestone", 60, 60,
                    start.plusHours(1), start.plusHours(1), 0, 0, 0, true);
            chart.show(new PlanResult(List.of(critical, other, milestone), 60, 3));
            chart.applyCss();
            chart.layout();
            Timeline timeline = assertInstanceOf(Timeline.class, chart.getCenter());
            assertEquals(3, timeline.getItems().size());
            TaskLine first = (TaskLine) timeline.getItems().get(0);
            assertEquals(start.toInstant(ZoneOffset.UTC), first.getItems().get(0).getStartTime());
            assertEquals(3600, first.getItems().get(0).getExpectedDuration());
            assertEquals(Color.web("#c62828"), first.getItems().get(0).getTaskBackground());
            TaskLine second = (TaskLine) timeline.getItems().get(1);
            assertEquals(1800, second.getItems().get(0).getExpectedDuration());
            assertEquals(start.plusMinutes(30).toInstant(ZoneOffset.UTC), second.getIntervals().get(0).getStartTime());
            assertEquals(start.plusHours(1).toInstant(ZoneOffset.UTC), second.getIntervals().get(0).getEndTime());
            TaskLine third = (TaskLine) timeline.getItems().get(2);
            assertEquals(0, third.getItems().get(0).getExpectedDuration());
            assertEquals(1, third.getItems().get(0).getTimePoints().size());
            timeline.setViewPortDuration(600);
            ((Button) ((HBox) chart.getTop()).getChildren().get(0)).fire();
            assertTrue(timeline.getViewPortDuration() > 3600);
            chart.show(null);
            assertTrue(timeline.getItems().isEmpty());
            assertInstanceOf(Label.class, chart.getCenter());
            chart.show(new PlanResult(List.of(milestone), 0, 0));
            assertTrue(timeline.getMaxTime().isAfter(timeline.getMinTime()));
            chart.layout();
            var image = timeline.snapshot(null, null);
            boolean visibleMilestone = false;
            for (int x = 0; x < image.getWidth() && !visibleMilestone; x++) {
                for (int y = 0; y < image.getHeight(); y++) {
                    if (image.getPixelReader().getColor(x, y).equals(Color.web("#c62828"))) {
                        visibleMilestone = true;
                        break;
                    }
                }
            }
            assertTrue(visibleMilestone, "Zero-duration milestone must actually be drawn");
            chart.show(new PlanResult(List.of(), 0, 0));
            assertEquals("No activities to schedule.", ((Label) chart.getCenter()).getText());
        });
    }

    @Test
    void kanbanShowsAllFieldsAndKeepsGrouping() throws Exception {
        var injector = Guice.createInjector(new AppModule(dir.resolve("views.db")));
        try {
            onFxThread(() -> {
                ProjectContext context = injector.getInstance(ProjectContext.class);
                ActivityDto activity = new ActivityDto();
                activity.setName("A long activity title that should wrap rather than widen the column");
                activity.setStatus("In progress");
                activity.setCategory("Design");
                activity.setImpact(42);
                activity.setResources(3);
                activity.getTags().add("UX");
                context.saveActivity(activity);
                try {
                    FXMLLoader loader = new FXMLLoader(getClass().getResource("/net/talaatharb/activitydag/ui/kanban-tab.fxml"));
                    loader.setControllerFactory(injector::getInstance);
                    Parent root = loader.load();
                    new Scene(root, 1000, 600);
                    root.applyCss();
                    root.layout();
                    assertEquals(2, root.lookupAll(".kanban-column").size());
                    VBox card = (VBox) root.lookup(".kanban-card");
                    assertTrue(card.getWidth() <= 290 - 24, "Long text must not widen a column");
                    assertEquals("Status: In progress", ((Label) card.lookup(".kanban-status")).getText());
                    assertEquals("Category: Design", ((Label) card.lookup(".kanban-category")).getText());
                    assertTrue(card.lookupAll(".kanban-metric").stream()
                            .anyMatch(n -> ((Label) n).getText().equals("Impact: 42")));
                    assertTrue(card.lookupAll(".kanban-metric").stream()
                            .anyMatch(n -> ((Label) n).getText().equals("Resources: 3")));
                    assertEquals("UX", ((Label) card.lookup(".kanban-tag")).getText());
                    assertFalse(root.lookupAll(".kanban-count").isEmpty());
                    ((RadioButton) root.lookup("#categoryRadio")).fire();
                    assertEquals(2, root.lookupAll(".kanban-column").size());
                    ActivityDto unassigned = new ActivityDto();
                    unassigned.setName("Unassigned task");
                    context.saveActivity(unassigned);
                    root.applyCss();
                    root.layout();
                    assertEquals(2, root.lookupAll(".kanban-card").size());
                    assertTrue(root.lookupAll(".kanban-status").stream()
                            .anyMatch(n -> ((Label) n).getText().equals("Status: Unassigned")));
                    assertTrue(root.lookupAll(".kanban-category").stream()
                            .anyMatch(n -> ((Label) n).getText().equals("Category: Unassigned")));

                    FXMLLoader planning = new FXMLLoader(getClass().getResource("/net/talaatharb/activitydag/ui/planning-tab.fxml"));
                    planning.setControllerFactory(injector::getInstance);
                    Parent planningRoot = planning.load();
                    new Scene(planningRoot, 1000, 600);
                    planningRoot.applyCss();
                    planningRoot.layout();
                    assertInstanceOf(GanttChart.class, planning.getNamespace().get("ganttChart"));
                    FXMLLoader main = new FXMLLoader(getClass().getResource("/net/talaatharb/activitydag/ui/main.fxml"));
                    main.setControllerFactory(injector::getInstance);
                    main.load();
                } catch (java.io.IOException e) {
                    throw new AssertionError("View did not load", e);
                }
            });
        } finally {
            injector.getInstance(MapDbStorage.class).close();
        }

    }

    @Test
    void orderingTagsSelectionAndFiltersWorkAcrossViews() throws Exception {
        var injector = Guice.createInjector(new AppModule(dir.resolve("filters.db")));
        try {
            onFxThread(() -> {
                ProjectContext context = injector.getInstance(ProjectContext.class);
                Instant day = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant();
                ActivityDto late = savedActivity(context, "Late", "Open", "Work", day.plusSeconds(7200));
                ActivityDto undated = savedActivity(context, "Undated", "Open", "Work", null);
                ActivityDto early = savedActivity(context, "Early", "Open", "Work", day.plusSeconds(3600));
                early.getTags().addAll(List.of("UX", "A long tag that must wrap without being clipped"));
                context.saveActivity(early);
                savedActivity(context, "Other", "Done", "Personal", day.plusSeconds(10800));
                try {
                    FXMLLoader kanban = viewLoader("kanban-tab.fxml", injector);
                    Parent boardRoot = kanban.load();
                    new Scene(boardRoot, 1000, 350);
                    boardRoot.applyCss();
                    boardRoot.layout();
                    HBox board = (HBox) kanban.getNamespace().get("board");
                    VBox openColumn = board.getChildren().stream().map(VBox.class::cast)
                            .filter(column -> ((Label) column.lookup(".kanban-heading")).getText().equals("Open"))
                            .findFirst().orElseThrow();
                    List<Node> cards = openColumn.getChildren().stream()
                            .filter(n -> n.getStyleClass().contains("kanban-card")).toList();
                    assertEquals(List.of(early.getId(), late.getId(), undated.getId()),
                            cards.stream().map(Node::getUserData).toList());
                    VBox earlyCard = (VBox) cards.get(0);
                    var badges = earlyCard.lookupAll(".kanban-tag");
                    assertEquals(2, badges.size());
                    for (Node badge : badges) {
                        assertTrue(badge.getBoundsInLocal().getHeight() > 0);
                        var position = earlyCard.sceneToLocal(badge.localToScene(badge.getBoundsInLocal()));
                        assertTrue(position.getMaxY() <= earlyCard.getHeight(),
                                "Tags must fit inside the card even with a short viewport");
                    }

                    FXMLLoader activities = viewLoader("activity-tab.fxml", injector);
                    activities.load();
                    TableView<?> table = (TableView<?>) activities.getNamespace().get("table");
                    table.getSelectionModel().clearAndSelect(0);
                    table.getSelectionModel().clearAndSelect(1);
                    assertEquals(1, table.getSelectionModel().getSelectedIndex());

                    FXMLLoader calendar = viewLoader("calendar-tab.fxml", injector);
                    Parent calendarRoot = calendar.load();
                    new Scene(calendarRoot, 1000, 600);
                    calendarRoot.applyCss();
                    calendarRoot.layout();
                    StackPane calendarPane = (StackPane) calendar.getNamespace().get("viewPane");
                    DateControl calendarView = (DateControl) calendarPane.getChildren().get(0);
                    com.calendarfx.model.Calendar<?> entries =
                            calendarView.getCalendarSources().get(0).getCalendars().get(0);
                    assertEquals(3, entries.findEntries(LocalDate.now(), LocalDate.now(),
                            ZoneId.systemDefault()).values().stream().mapToInt(List::size).sum());

                    FXMLLoader planning = viewLoader("planning-tab.fxml", injector);
                    Parent planningRoot = planning.load();
                    new Scene(planningRoot, 1000, 600);
                    planningRoot.applyCss();
                    planningRoot.layout();
                    Button plan = planningRoot.lookupAll(".button").stream().map(Button.class::cast)
                            .filter(b -> b.getText().equals("Plan")).findFirst().orElseThrow();
                    plan.fire();
                    GanttChart chart = (GanttChart) planning.getNamespace().get("ganttChart");
                    assertEquals(4, ((Timeline) chart.getCenter()).getItems().size());

                    CheckBox openFilter = filterBox((Parent) kanban.getNamespace().get("filterBar"), "Open");
                    openFilter.fire();
                    boardRoot.applyCss();
                    assertEquals(1, context.getActivities().size());
                    assertEquals(1, boardRoot.lookupAll(".kanban-card").size());
                    assertFalse(filterBox((Parent) calendar.getNamespace().get("filterBar"), "Open").isSelected());
                    assertFalse(filterBox((Parent) planning.getNamespace().get("filterBar"), "Open").isSelected());
                    assertEquals(1, entries.findEntries(LocalDate.now(), LocalDate.now(),
                            ZoneId.systemDefault()).values().stream().mapToInt(List::size).sum());
                    assertEquals(1, ((Timeline) chart.getCenter()).getItems().size());

                    filterBox((Parent) calendar.getNamespace().get("filterBar"), "Open").fire();
                    assertEquals(4, context.getActivities().size());
                    assertTrue(openFilter.isSelected());
                    assertEquals(4, ((Timeline) chart.getCenter()).getItems().size());
                    context.getHiddenCategories().add("Work");
                    assertEquals(1, context.getActivities().size());
                    assertEquals(1, ((Timeline) chart.getCenter()).getItems().size());
                    context.getHiddenCategories().remove("Work");
                    assertEquals(4, ((Timeline) chart.getCenter()).getItems().size());
                    context.getHiddenStatuses().add("Done");
                    context.getHiddenStatuses().add("Open");
                    assertTrue(context.getActivities().isEmpty());
                    assertInstanceOf(Label.class, chart.getCenter());
                    context.getHiddenStatuses().clear();
                    assertEquals(4, ((Timeline) chart.getCenter()).getItems().size());
                } catch (java.io.IOException e) {
                    throw new AssertionError("View did not load", e);
                }
            });
        } finally {
            injector.getInstance(MapDbStorage.class).close();
        }
    }

    private static ActivityDto savedActivity(ProjectContext context, String name, String status, String category,
            Instant start) {
        ActivityDto activity = new ActivityDto();
        activity.setName(name);
        activity.setStatus(status);
        activity.setCategory(category);
        activity.setStartDate(start);
        if (start != null) {
            activity.setEndDate(start.plusSeconds(1800));
        }
        return context.saveActivity(activity);
    }

    private FXMLLoader viewLoader(String file, com.google.inject.Injector injector) {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/net/talaatharb/activitydag/ui/" + file));
        loader.setControllerFactory(injector::getInstance);
        return loader;
    }

    private static CheckBox filterBox(Parent filter, String name) {
        filter.applyCss();
        return filter.lookupAll(".check-box").stream().map(CheckBox.class::cast)
                .filter(box -> box.getText().equals(name)).findFirst().orElseThrow();
    }
}
