package net.talaatharb.activitydag.ui;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.StackPane;
import net.talaatharb.activitydag.dto.ActivityDto;
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
    private final Calendar calendar = new Calendar("Activities");
    private final CalendarSource source = new CalendarSource("Project");
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
        filterBar.init(context);
        calendar.setStyle(Style.STYLE1);
        source.getCalendars().add(calendar);

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
        context.getActivities().addListener((ListChangeListener<ActivityDto>) c -> rebuild());
        setMode(Mode.THREE_DAYS);
        rebuild();
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
        listeners.forEach((entry, l) -> entry.intervalProperty().removeListener(l));
        listeners.clear();
        pending.clear();
        calendar.clear();
        for (ActivityDto a : context.getActivities()) {
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
            calendar.addEntry(entry);
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
        boolean failed = false;
        updating = true;
        try {
            for (Entry<ActivityDto> entry : changed) {
                ActivityDto a = entry.getUserObject();
                Instant oldStart = a.getStartDate();
                Instant oldEnd = a.getEndDate();
                a.setStartDate(entry.getStartAsZonedDateTime().toInstant());
                a.setEndDate(entry.getEndAsZonedDateTime().toInstant());
                try {
                    context.saveActivity(a);
                } catch (IllegalArgumentException ex) {
                    a.setStartDate(oldStart);
                    a.setEndDate(oldEnd);
                    failed = true;
                }
            }
        } finally {
            updating = false;
        }
        if (failed) {
            context.reloadActivities();
        }
    }
}
