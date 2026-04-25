package org;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/*
Owns month calendar rendering, navigation, and live date/time marker.
*/
public class MonthViewController {
    private static final DateTimeFormatter LIVE_DATE_TIME_12_FORMAT = DateTimeFormatter.ofPattern("EEE, MMM d yyyy  h:mm:ss a", Locale.US);
    private static final DateTimeFormatter LIVE_DATE_TIME_24_FORMAT = DateTimeFormatter.ofPattern("EEE, MMM d yyyy  HH:mm:ss", Locale.US);

    private final Settings settings;
    private final Supplier<UserAccount> currentUserSupplier;
    private final Supplier<List<Event>> allEventsSupplier;
    private final Supplier<LocalDate> selectedDateSupplier;
    private final Consumer<LocalDate> onDateSelected;
    private final Consumer<LocalDate> onQuickAddRequested;
    private final BiConsumer<LocalDate, List<Event>> onDayEventsRequested;
    private final Consumer<Event> onEventClicked;
    private final Runnable onClearDayFilter;

    private Label monthLabel;
    private Label liveDateTimeLabel;
    private GridPane monthGrid;
    private ListView<String> monthRemindersListView;
    private YearMonth visibleMonth = YearMonth.now();
    private Timeline liveDateTimeTimeline;

    public MonthViewController(
        Settings settings,
        Supplier<UserAccount> currentUserSupplier,
        Supplier<List<Event>> allEventsSupplier,
        Supplier<LocalDate> selectedDateSupplier,
        Consumer<LocalDate> onDateSelected,
        Consumer<LocalDate> onQuickAddRequested,
        BiConsumer<LocalDate, List<Event>> onDayEventsRequested,
        Consumer<Event> onEventClicked,
        Runnable onClearDayFilter
    ) {
        this.settings = settings;
        this.currentUserSupplier = currentUserSupplier;
        this.allEventsSupplier = allEventsSupplier;
        this.selectedDateSupplier = selectedDateSupplier;
        this.onDateSelected = onDateSelected;
        this.onQuickAddRequested = onQuickAddRequested;
        this.onDayEventsRequested = onDayEventsRequested;
        this.onEventClicked = onEventClicked;
        this.onClearDayFilter = onClearDayFilter;
    }

    public VBox buildCalendarPanel() {
        monthLabel = new Label();
        monthLabel.getStyleClass().add("month-label");

        liveDateTimeLabel = new Label();
        liveDateTimeLabel.getStyleClass().add("live-datetime-label");

        Button previousMonthBtn = new Button("<");
        previousMonthBtn.setOnAction(e -> {
            visibleMonth = visibleMonth.minusMonths(1);
            rebuildMonthGrid();
        });

        Button nextMonthBtn = new Button(">") ;
        nextMonthBtn.setOnAction(e -> {
            visibleMonth = visibleMonth.plusMonths(1);
            rebuildMonthGrid();
        });

        Button clearFilterBtn = new Button("Clear Day Filter");
        clearFilterBtn.setOnAction(e -> onClearDayFilter.run());

        Region monthHeaderSpacer = new Region();
        HBox.setHgrow(monthHeaderSpacer, Priority.ALWAYS);

        HBox monthHeader = new HBox(10, previousMonthBtn, monthLabel, nextMonthBtn, clearFilterBtn, monthHeaderSpacer, liveDateTimeLabel);
        monthHeader.setAlignment(Pos.CENTER_LEFT);

        monthGrid = new GridPane();
        monthGrid.getStyleClass().add("month-grid");
        monthGrid.setHgap(4);
        monthGrid.setVgap(4);

        for (int col = 0; col < 7; col++) {
            ColumnConstraints cc = new ColumnConstraints();
            cc.setPercentWidth(100.0 / 7.0);
            cc.setHgrow(Priority.ALWAYS);
            monthGrid.getColumnConstraints().add(cc);
        }

        Label monthRemindersTitle = new Label("Upcoming Reminders");
        monthRemindersTitle.getStyleClass().add("section-title");
        monthRemindersListView = new ListView<>();
        monthRemindersListView.setPlaceholder(new Label("No reminders yet."));
        monthRemindersListView.setPrefHeight(150);
        monthRemindersListView.setMaxHeight(180);

        VBox panel = new VBox(10, monthHeader, monthGrid, monthRemindersTitle, monthRemindersListView);
        VBox.setVgrow(monthGrid, Priority.ALWAYS);

        rebuildMonthGrid();
        startLiveDateTimeClock();
        return panel;
    }

    public void rebuildMonthGrid() {
        if (monthGrid == null || monthLabel == null) {
            return;
        }

        monthGrid.getChildren().clear();
        monthLabel.setText(visibleMonth.getMonth() + " " + visibleMonth.getYear());

        String[] dayHeaders = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
        for (int col = 0; col < dayHeaders.length; col++) {
            Label day = new Label(dayHeaders[col]);
            day.setMaxWidth(Double.MAX_VALUE);
            day.setAlignment(Pos.CENTER);
            day.getStyleClass().add("day-header");
            monthGrid.add(day, col, 0);
        }

        Map<LocalDate, List<Event>> eventsByDate = new HashMap<>();
        LocalDate firstOfMonth = visibleMonth.atDay(1);
        LocalDate lastOfMonth = visibleMonth.atEndOfMonth();
        if (currentUserSupplier.get() != null) {
            for (Event event : allEventsSupplier.get()) {
                for (LocalDate date = firstOfMonth; !date.isAfter(lastOfMonth); date = date.plusDays(1)) {
                    if (EventService.occursOn(event, date)) {
                        eventsByDate.computeIfAbsent(date, k -> new ArrayList<>())
                            .add(EventService.asOccurrence(event, date));
                    }
                }
            }
        }
        int firstColumn = toSundayIndex(firstOfMonth.getDayOfWeek());
        int daysInMonth = visibleMonth.lengthOfMonth();

        int day = 1;
        for (int row = 1; row <= 6; row++) {
            for (int col = 0; col < 7; col++) {
                VBox dayCell = new VBox(4);
                dayCell.setMinHeight(72);
                dayCell.setPadding(new Insets(6));
                dayCell.setStyle(baseDayCellStyle());

                boolean inThisMonth = (row == 1 && col >= firstColumn) || (row > 1 && day <= daysInMonth);
                if (inThisMonth && day <= daysInMonth) {
                    LocalDate date = visibleMonth.atDay(day);
                    Label dayNumber = new Label(String.valueOf(day));
                    dayNumber.getStyleClass().add("day-number");
                    dayCell.getChildren().add(dayNumber);

                    List<Event> eventsOnDate = eventsByDate.getOrDefault(date, List.of());
                    int count = eventsOnDate.size();
                    if (count > 0) {
                        Label eventBadge = new Label(count + (count == 1 ? " event" : " events"));
                        eventBadge.getStyleClass().add("event-badge");
                        dayCell.getChildren().add(eventBadge);

                        int maxTitlesInCell = Math.min(2, count);
                        for (int i = 0; i < maxTitlesInCell; i++) {
                            Event event = eventsOnDate.get(i);
                            Button eventTitle = new Button(event.getTitle());
                            eventTitle.setMaxWidth(Double.MAX_VALUE);
                            eventTitle.setAlignment(Pos.CENTER_LEFT);
                            eventTitle.getStyleClass().add("day-event-button");
                            eventTitle.setOnAction(e -> onEventClicked.accept(event));
                            dayCell.getChildren().add(eventTitle);
                        }

                        if (count > maxTitlesInCell) {
                            Label more = new Label("+" + (count - maxTitlesInCell) + " more");
                            more.getStyleClass().add("more-label");
                            dayCell.getChildren().add(more);
                        }

                        dayCell.setStyle(eventDayCellStyle());
                    }

                    PauseTransition singleClickDelay = new PauseTransition(Duration.millis(220));
                    singleClickDelay.setOnFinished(e -> {
                        onDateSelected.accept(date);
                        onQuickAddRequested.accept(date);
                    });

                    dayCell.setOnMouseClicked(e -> {
                        if (e.getClickCount() >= 2) {
                            singleClickDelay.stop();
                            onDateSelected.accept(date);
                            onDayEventsRequested.accept(date, eventsOnDate);
                            return;
                        }

                        if (e.getClickCount() == 1) {
                            singleClickDelay.playFromStart();
                        }
                    });

                    if (date.equals(LocalDate.now())) {
                        dayCell.setStyle(dayCell.getStyle() + todayBorderStyle());
                    }

                    LocalDate selectedDate = selectedDateSupplier.get();
                    if (selectedDate != null && date.equals(selectedDate)) {
                        dayCell.setStyle(selectedDayCellStyle());
                    }

                    installDayHoverEffects(dayCell, date, eventsOnDate);
                    day++;
                } else {
                    dayCell.setStyle(emptyDayCellStyle());
                }

                monthGrid.add(dayCell, col, row);
            }
        }
    }

    public void startLiveDateTimeClock() {
        stopLiveDateTimeClock();
        refreshLiveDateTimeMarker();

        liveDateTimeTimeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> refreshLiveDateTimeMarker()));
        liveDateTimeTimeline.setCycleCount(Timeline.INDEFINITE);
        liveDateTimeTimeline.play();
    }

    public void stopLiveDateTimeClock() {
        if (liveDateTimeTimeline != null) {
            liveDateTimeTimeline.stop();
            liveDateTimeTimeline = null;
        }
    }

    public void refreshLiveDateTimeMarker() {
        if (liveDateTimeLabel == null) {
            return;
        }

        DateTimeFormatter formatter = Settings.TIME_FORMAT_24.equals(settings.getTimeFormat())
            ? LIVE_DATE_TIME_24_FORMAT
            : LIVE_DATE_TIME_12_FORMAT;
        liveDateTimeLabel.setText(LocalDateTime.now().format(formatter));
    }

    public ListView<String> getMonthRemindersListView() {
        return monthRemindersListView;
    }

    private int toSundayIndex(DayOfWeek dayOfWeek) {
        if (dayOfWeek == DayOfWeek.SUNDAY) {
            return 0;
        }
        return dayOfWeek.getValue();
    }

    private String baseDayCellStyle() {
        if (settings.isDarkTheme()) {
            return "-fx-border-color: #374151; -fx-border-width: 1; -fx-background-color: #111827;";
        }
        return "-fx-border-color: #d2d7dd; -fx-border-width: 1; -fx-background-color: #ffffff;";
    }

    private String emptyDayCellStyle() {
        if (settings.isDarkTheme()) {
            return "-fx-border-color: #374151; -fx-border-width: 1; -fx-background-color: #1f2937;";
        }
        return "-fx-border-color: #d2d7dd; -fx-border-width: 1; -fx-background-color: #f7f8fa;";
    }

    private String eventDayCellStyle() {
        if (settings.isDarkTheme()) {
            return "-fx-border-color: #1f8a5b; -fx-border-width: 1; -fx-background-color: #102a22;";
        }
        return "-fx-border-color: #7bcfa4; -fx-border-width: 1; -fx-background-color: #effaf3;";
    }

    private String selectedDayCellStyle() {
        if (settings.isDarkTheme()) {
            return "-fx-border-color: #f59e0b; -fx-border-width: 2; -fx-background-color: #3b2c14;";
        }
        return "-fx-border-color: #f59e0b; -fx-border-width: 2; -fx-background-color: #fff7e6;";
    }

    private String hoverDayCellStyle(boolean hasEvents) {
        if (settings.isDarkTheme()) {
            return hasEvents
                ? "-fx-border-color: #5eead4; -fx-border-width: 1; -fx-background-color: #1b2a2f;"
                : "-fx-border-color: #6b7280; -fx-border-width: 1; -fx-background-color: #1f2630;";
        }
        return hasEvents
            ? "-fx-border-color: #3b82f6; -fx-border-width: 1; -fx-background-color: #eaf2ff;"
            : "-fx-border-color: #94a3b8; -fx-border-width: 1; -fx-background-color: #f8fbff;";
    }

    private void installDayHoverEffects(VBox dayCell, LocalDate date, List<Event> eventsOnDate) {
        boolean hasEvents = !eventsOnDate.isEmpty();
        String normalStyle = dayCell.getStyle();

        dayCell.setOnMouseEntered(e -> {
            LocalDate selectedDate = selectedDateSupplier.get();
            if (selectedDate != null && date.equals(selectedDate)) {
                dayCell.setStyle(selectedDayCellStyle());
            } else if (date.equals(LocalDate.now())) {
                dayCell.setStyle(normalStyle + todayHoverBorderStyle());
            } else {
                dayCell.setStyle(hoverDayCellStyle(hasEvents));
            }
        });

        dayCell.setOnMouseExited(e -> {
            LocalDate selectedDate = selectedDateSupplier.get();
            if (selectedDate != null && date.equals(selectedDate)) {
                dayCell.setStyle(selectedDayCellStyle());
            } else if (hasEvents) {
                dayCell.setStyle(eventDayCellStyle());
            } else if (date.equals(LocalDate.now())) {
                dayCell.setStyle(normalStyle + todayBorderStyle());
            } else {
                dayCell.setStyle(normalStyle);
            }
        });
    }

    private String todayBorderStyle() {
        if (settings.isDarkTheme()) {
            return "-fx-border-color: #5eead4; -fx-border-width: 2;";
        }
        return "-fx-border-color: #2f6feb; -fx-border-width: 2;";
    }

    private String todayHoverBorderStyle() {
        if (settings.isDarkTheme()) {
            return "-fx-border-color: #99f6e4; -fx-border-width: 2;";
        }
        return "-fx-border-color: #60a5fa; -fx-border-width: 2;";
    }
}
