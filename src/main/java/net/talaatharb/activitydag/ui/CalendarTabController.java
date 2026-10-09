package net.talaatharb.activitydag.ui;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.HashMap;
import java.util.UUID;

import com.calendarfx.model.Calendar;
import com.calendarfx.model.Calendar.Style;
import com.calendarfx.model.CalendarSource;
import com.calendarfx.model.Entry;
import com.calendarfx.model.Interval;
import com.calendarfx.view.DateControl;
import com.calendarfx.view.DetailedWeekView;
import com.calendarfx.view.MonthView;
import com.google.inject.Inject;

import javafx.animation.PauseTransition;
import javafx.collections.ListChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import net.talaatharb.activitydag.dto.ActivityDto;
import net.talaatharb.activitydag.dto.ProjectDto;
import net.talaatharb.activitydag.model.DurationUnit;

/**
 * Calendar of the (filtered) activities using CalendarFX: 3-day (stepped by one day), week and month views.
 * Activities can be dragged to move them, or resized to change their start / end time.
 */
public class CalendarTabController {
    private static final DateTimeFormatter TITLE = DateTimeFormatter.ofPattern("MMM d, yyyy");
    private static final DateTimeFormatter MONTH_TITLE = DateTimeFormatter.ofPattern("MMMM yyyy");

    private enum Mode { THREE_DAYS, WEEK, MONTH }

    private final ProjectContext context;
    private final ZoneId zone = ZoneId.systemDefault();
    private final CalendarSource source = new CalendarSource("Projects");
    private final Map<UUID, Calendar<ActivityDto>> calendars = new HashMap<>();
    private final Set<UUID> selectedProjects = new HashSet<>();
    private final ObservableList<ActivityDto> calendarActivities = FXCollections.observableArrayList();
    private final Map<Entry<ActivityDto>, javafx.beans.value.ChangeListener<Interval>> listeners =
            new java.util.IdentityHashMap<>();
    private final PauseTransition saveDelay = new PauseTransition(javafx.util.Duration.millis(400));
    private final List<Entry<ActivityDto>> pending = new ArrayList<>();

    private DetailedWeekView threeDayView;
    private DetailedWeekView weekView;
    private MonthView monthView;
    private LocalDate anchor = LocalDate.now();
    private Mode mode = Mode.THREE_DAYS;
    private boolean updating;

    @FXML private FilterBar filterBar;
    @FXML private VBox projectChecks;
    @FXML private StackPane viewPane;
    @FXML private Label titleLabel;
    @FXML private ToggleButton threeDaysButton;
    @FXML private ToggleButton weekButton;
    @FXML private ToggleButton monthButton;

    @Inject
    public CalendarTabController(ProjectContext context) {
        this.context = context;
    }

    @FXML
    private void initialize() {
        filterBar.init(context, calendarActivities);
        selectCurrentProject();

        threeDayView = new DetailedWeekView(3);
        threeDayView.getWeekView().setAdjustToFirstDayOfWeek(false);
        weekView = new DetailedWeekView(7);
        monthView = new MonthView();
        for (DateControl view : List.of(threeDayView.getWeekView(), weekView.getWeekView(), monthView)) {
            configure(view);
        }
        configure(threeDayView);
        configure(weekView);
        viewPane.getChildren().setAll(threeDayView, weekView, monthView);

        ToggleGroup group = new ToggleGroup();
        threeDaysButton.setToggleGroup(group);
        weekButton.setToggleGroup(group);
        monthButton.setToggleGroup(group);
        threeDaysButton.setOnAction(e -> setMode(Mode.THREE_DAYS));
        weekButton.setOnAction(e -> setMode(Mode.WEEK));
        monthButton.setOnAction(e -> setMode(Mode.MONTH));
        threeDaysButton.setSelected(true);

        saveDelay.setOnFinished(e -> flush());
        context.getAllActivities().addListener((ListChangeListener<ActivityDto>) c -> rebuild());
        context.getHiddenStatuses().addListener((javafx.collections.SetChangeListener<String>) c -> rebuild());
        context.getHiddenCategories().addListener((javafx.collections.SetChangeListener<String>) c -> rebuild());
        context.getProjects().addListener((ListChangeListener<ProjectDto>) c -> {
            selectedProjects.retainAll(context.getProjects().stream().map(ProjectDto::getId).toList());
            rebuildProjectChecks();
            rebuild();
        });
        context.currentProjectProperty().addListener((obs, old, now) -> {
            if (old == null || now == null || !old.getId().equals(now.getId())) {
                selectCurrentProject();
                rebuild();
            }
        });
        setMode(Mode.THREE_DAYS);
        rebuild();
    }

    private void selectCurrentProject() {
        selectedProjects.clear();
        if (context.getCurrentProject() != null) {
            selectedProjects.add(context.getCurrentProject().getId());
        }
        rebuildProjectChecks();
    }

    private void rebuildProjectChecks() {
        projectChecks.getChildren().clear();
        for (ProjectDto project : context.getProjects()) {
            CheckBox check = new CheckBox(project.getName());
            check.setUserData(project.getId());
            check.setWrapText(true);
            check.setSelected(selectedProjects.contains(project.getId()));
            check.selectedProperty().addListener((obs, old, now) -> {
                if (now) {
                    selectedProjects.add(project.getId());
                } else {
                    selectedProjects.remove(project.getId());
                }
                rebuild();
            });
            projectChecks.getChildren().add(check);
        }
    }

    private void configure(DateControl view) {
        view.getCalendarSources().setAll(source);
        view.setEntryFactory(param -> null);
        view.setEntryDetailsCallback(param -> true);
        view.setContextMenuCallback(param -> null);
        view.setEntryContextMenuCallback(param -> null);
    }

    @FXML
    private void onPrevious() {
        move(-1);
    }

    @FXML
    private void onNext() {
        move(1);
    }

    @FXML
    private void onToday() {
        anchor = LocalDate.now();
        updateViews();
    }

    private void move(int direction) {
        anchor = switch (mode) {
            case THREE_DAYS -> anchor.plusDays(direction);
            case WEEK -> anchor.plusWeeks(direction);
            case MONTH -> anchor.plusMonths(direction);
        };
        updateViews();
    }

    private void setMode(Mode newMode) {
        mode = newMode;
        threeDayView.setVisible(mode == Mode.THREE_DAYS);
        weekView.setVisible(mode == Mode.WEEK);
        monthView.setVisible(mode == Mode.MONTH);
        for (Node n : viewPane.getChildren()) {
            n.setManaged(n.isVisible());
        }
        updateViews();
    }

    private void updateViews() {
        LocalDate today = LocalDate.now();
        for (DateControl v : List.of(threeDayView, weekView, monthView)) {
            v.setToday(today);
        }
        // 3-day view: the anchor day is the middle one (yesterday, today, tomorrow by default)
        threeDayView.setDate(anchor.minusDays(1));
        threeDayView.getWeekView().setDate(anchor.minusDays(1));
        weekView.setDate(anchor);
        weekView.getWeekView().setDate(anchor);
        monthView.setDate(anchor);
        titleLabel.setText(switch (mode) {
            case THREE_DAYS -> anchor.minusDays(1).format(TITLE) + " – " + anchor.plusDays(1).format(TITLE);
            case WEEK -> weekView.getWeekView().getStartDate().format(TITLE) + " – "
                    + weekView.getWeekView().getStartDate().plusDays(6).format(TITLE);
            case MONTH -> anchor.format(MONTH_TITLE);
        });
    }

    private void rebuild() {
        if (updating) {
            return;
        }
        if (!pending.isEmpty()) {
            flush();
            return;
        }
        saveDelay.stop();
        listeners.forEach((entry, l) -> entry.intervalProperty().removeListener(l));
        listeners.clear();
        calendars.values().forEach(Calendar::clear);
        calendars.keySet().retainAll(context.getProjects().stream().map(ProjectDto::getId).toList());
        source.getCalendars().clear();
        List<ActivityDto> available = new ArrayList<>();
        for (ProjectDto project : context.getProjects()) {
            if (!selectedProjects.contains(project.getId())) {
                continue;
            }
            Calendar<ActivityDto> calendar = calendars.computeIfAbsent(project.getId(), id -> {
                Calendar<ActivityDto> created = new Calendar<>(project.getName());
                created.setStyle(Style.values()[Math.floorMod(id.hashCode(), Style.values().length)]);
                return created;
            });
            calendar.setName(project.getName());
            source.getCalendars().add(calendar);
            available.addAll(context.activitiesForProject(project.getId()));
        }
        calendarActivities.setAll(available);
        for (ActivityDto a : available) {
            if (context.getHiddenStatuses().contains(ProjectContext.key(a.getStatus()))
                    || context.getHiddenCategories().contains(ProjectContext.key(a.getCategory()))) {
                continue;
            }
            Interval interval = intervalOf(a);
            if (interval == null) {
                continue;
            }
            Entry<ActivityDto> entry = new Entry<>(a.getName(), interval);
            entry.setUserObject(a);
            javafx.beans.value.ChangeListener<Interval> l = (obs, old, now) -> {
                if (!pending.contains(entry)) {
                    pending.add(entry);
                }
                saveDelay.playFromStart();
            };
            entry.intervalProperty().addListener(l);
            listeners.put(entry, l);
            calendars.get(a.getProjectId()).addEntry(entry);
        }
        updateViews();
    }

    /** Interval of an activity; an activity with only one date is shown using its duration. */
    private Interval intervalOf(ActivityDto a) {
        Instant start = a.getStartDate();
        Instant end = a.getEndDate();
        if (start == null && end == null) {
            return null;
        }
        Duration length = Duration.ofMinutes(Math.max(30,
                (a.getDurationUnit() == null ? DurationUnit.DAYS : a.getDurationUnit()).toMinutes(a.getDuration())));
        if (start == null) {
            start = end.minus(length);
        } else if (end == null || end.isBefore(start)) {
            end = start.plus(length);
        }
        return new Interval(start.atZone(zone), end.atZone(zone));
    }

    private void flush() {
        List<Entry<ActivityDto>> changed = new ArrayList<>(pending);
        pending.clear();
        List<String> errors = new ArrayList<>();
        updating = true;
        try {
            for (Entry<ActivityDto> entry : changed) {
                ActivityDto a = entry.getUserObject();
                try {
                    context.updateActivityTiming(a.getId(), a.getProjectId(),
                            entry.getStartAsZonedDateTime().toInstant(), entry.getEndAsZonedDateTime().toInstant());
                } catch (IllegalArgumentException ex) {
                    errors.add(a.getName() + ": " + ex.getMessage());
                }
            }
        } finally {
            updating = false;
        }
        rebuild();
        if (!errors.isEmpty()) {
            Alert alert = new Alert(AlertType.ERROR, String.join("\n", errors));
            alert.setHeaderText("Could not update activity timing");
            alert.showAndWait();
        }
    }
}
