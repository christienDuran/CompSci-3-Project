package org;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Accordion;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

/**
 * JavaFX desktop UI for account auth, event creation, month-view browsing, and reminder handling.
 *
 * Flow overview:
 * - Login or create an account.
 * - Save events and optionally add custom reminders.
 * - Auto-create a start-time reminder for every event.
 * - Browse the month grid, click days to filter events, and click reminders or events for details.
 */
public class CalendarFxApp extends Application {
    private static final String GEOCODING_URL = "https://geocoding-api.open-meteo.com/v1/search";
    private static final DateTimeFormatter REMINDER_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd h:mm a");
    private static final DateTimeFormatter REMINDER_LIST_TIME_FORMAT = DateTimeFormatter.ofPattern("EEE h:mm a");

    private Stage stage;
    private Scene currentEventsScene;
    private BorderPane eventsRoot;
    private SplitPane contentSplit;
    private VBox calendarPanel;
    private TabPane sideTabs;

    private TextField usernameField;
    private PasswordField passwordField;
    // Login preference: when checked, session is restored automatically on next launch.
    private CheckBox rememberMeCheck;
    private Label authStatusLabel;
    private Label formStatusLabel;

    private TextField titleField;
    private TextField descriptionField;
    private DatePicker datePicker;
    private ComboBox<String> startTimeBox;
    private ComboBox<String> endTimeBox;
    private ComboBox<String> themeBox;
    private ComboBox<String> timeFormatBox;
    private CheckBox sidebarToggle;
    private CheckBox recurringCheck;
    private CheckBox autoReminderCheck;
    private CheckBox reminderCheck;
    private TextField reminderMinutesField;
    private TextField snoozeMinutesField;
    private ComboBox<String> weatherUnitBox;
    private ComboBox<String> weatherPlaceBox;
    private TextField weatherLatitudeField;
    private TextField weatherLongitudeField;
    private TextField goalNameField;
    private TextField goalCurrentField;
    private TextField goalTargetField;
    private Label goalStatusLabel;
    private Label eventListTitle;
    private ListView<String> eventsListView;
    private List<Event> displayedEvents = new ArrayList<>();
    private ListView<String> remindersListView;
    private Label weatherHeaderLabel;
    private VBox weatherCardsBox;
    private ScrollPane weatherScrollPane;
    private VBox goalsContainer;
    private Label userLabel;
    private Label monthLabel;
    private GridPane monthGrid;
    // Inline reminder strip shown directly under the month grid.
    private ListView<String> monthRemindersListView;
    private YearMonth visibleMonth;
    private LocalDate selectedDate;
    private Timeline reminderPoller;
    // Guard to prevent multiple reminder notification windows at once.
    private boolean reminderNotificationOpen;
    private WeatherService weatherService;
    private final Map<String, WeatherLocation> weatherLocationLookup = new HashMap<>();
    private String currentWeatherLocationLabel = "";
    private double currentWeatherLatitude;
    private double currentWeatherLongitude;
    private List<WeatherService.ForecastEntry> currentWeatherForecast = new ArrayList<>();
    private final Settings settings = Settings.getInstance();

    private UserAccount currentUser;
    private final List<Reminder> reminders = new ArrayList<>();
    private final List<Goal> goals = new ArrayList<>();
    private final List<Event> allEvents = new ArrayList<>();
    private final Map<Integer, Event> eventsById = new HashMap<>();

    @Override
    public void start(Stage primaryStage) {
        this.stage = primaryStage;
        stage.setTitle("Extreme Calendar - JavaFX UI");
        // Attempt to restore a remembered login before showing the auth screen.
        if (!tryRestorePersistedSession()) {
            stage.setScene(buildAuthScene());
        }
        stage.setMinWidth(1100);
        stage.setMinHeight(700);
        stage.setWidth(1360);
        stage.setHeight(860);
        stage.widthProperty().addListener((obs, oldW, newW) -> updateResponsiveLayout());
        stage.show();
    }

    private Scene buildAuthScene() {
        usernameField = new TextField();
        passwordField = new PasswordField();
        // Defaults to on so users stay signed in unless they opt out.
        rememberMeCheck = new CheckBox("Remember me");
        rememberMeCheck.setSelected(true);
        authStatusLabel = new Label(" ");
        authStatusLabel.getStyleClass().add("status-label");

        VBox root = new VBox(10);
        root.getStyleClass().add("auth-root");
        root.setPadding(new Insets(20));

        Button loginBtn = new Button("Login");
        loginBtn.setOnAction(e -> handleLogin());

        Button createBtn = new Button("Create Account");
        createBtn.setOnAction(e -> handleCreateAccount());

        HBox actions = new HBox(10, loginBtn, createBtn);

        root.getChildren().addAll(
            new Label("Username"),
            usernameField,
            new Label("Password"),
            passwordField,
            rememberMeCheck,
            actions,
            authStatusLabel
        );

        Scene scene = new Scene(root);
        applySceneStyles(scene);
        return scene;
    }

    private Scene buildEventsScene() {
        titleField = new TextField();
        descriptionField = new TextField();
        datePicker = new DatePicker(LocalDate.now());
        startTimeBox = buildTimePicker("9:00 AM");
        endTimeBox = buildTimePicker("10:00 AM");
        // Keeps end time valid whenever start time changes.
        bindEndTimeAfterStart(startTimeBox, endTimeBox);
        themeBox = new ComboBox<>();
        themeBox.getItems().addAll("Light", "Dark");
        themeBox.setValue(settings.isDarkTheme() ? "Dark" : "Light");
        themeBox.setOnAction(e -> {
            settings.changeTheme("Dark".equals(themeBox.getValue()) ? Settings.THEME_DARK : Settings.THEME_LIGHT);
            applyThemeToCurrentScene();
        });
        timeFormatBox = new ComboBox<>();
        timeFormatBox.getItems().addAll(Settings.TIME_FORMAT_12, Settings.TIME_FORMAT_24);
        timeFormatBox.setValue(settings.getTimeFormat());
        timeFormatBox.setOnAction(e -> {
            settings.changeTimeFormat(timeFormatBox.getValue());
            refreshEventList();
            refreshRemindersArea();
            rebuildMonthGrid();
        });
        sidebarToggle = new CheckBox("Show side panel");
        sidebarToggle.setSelected(settings.isSideBarOpen());
        sidebarToggle.setOnAction(e -> {
            settings.changeSideBarOpen(sidebarToggle.isSelected());
            updateResponsiveLayout();
        });
        recurringCheck = new CheckBox("Recurring");
        // Optional baseline reminder that fires at the event start time.
        autoReminderCheck = new CheckBox("Automatic reminder at start time");
        autoReminderCheck.setSelected(true);
        // Optional extra reminder that can fire earlier than the event start.
        reminderCheck = new CheckBox("Add custom early reminder");
        reminderMinutesField = new TextField("15");
        reminderMinutesField.setPromptText("e.g. 15");
        snoozeMinutesField = new TextField("10");
        snoozeMinutesField.setPromptText("e.g. 10");
        weatherUnitBox = new ComboBox<>();
        weatherUnitBox.getItems().addAll("Celsius (°C)", "Fahrenheit (°F)");
        weatherUnitBox.setValue(Settings.WEATHER_UNIT_FAHRENHEIT.equals(settings.getWeatherUnit())
            ? "Fahrenheit (°F)"
            : "Celsius (°C)");
        weatherUnitBox.setOnAction(e -> {
            settings.changeWeatherUnit(isFahrenheitWeatherUnit()
                ? Settings.WEATHER_UNIT_FAHRENHEIT
                : Settings.WEATHER_UNIT_CELSIUS);
            rerenderCurrentWeatherForecast();
        });
        weatherPlaceBox = new ComboBox<>();
        weatherPlaceBox.setEditable(true);
        weatherPlaceBox.setPromptText("e.g. New York");
        weatherPlaceBox.getEditor().setText("New York");
        weatherPlaceBox.setOnAction(e -> applySelectedWeatherPlace());
        weatherLatitudeField = new TextField("40.7128");
        weatherLongitudeField = new TextField("-74.0060");
        goalNameField = new TextField();
        goalCurrentField = new TextField("0");
        goalTargetField = new TextField("100");
        goalStatusLabel = new Label("Goal feature ready");
        goalStatusLabel.getStyleClass().add("status-label");
        formStatusLabel = new Label("Ready");
        formStatusLabel.getStyleClass().add("status-label");
        visibleMonth = YearMonth.now();
        selectedDate = null;
        weatherService = new WeatherService(parseDoubleOrDefault(weatherLatitudeField.getText(), 40.7128), parseDoubleOrDefault(weatherLongitudeField.getText(), -74.0060));

        userLabel = new Label("Not logged in");
        userLabel.getStyleClass().add("section-title");

        VBox form = new VBox(10);
        form.getStyleClass().add("form-pane");
        form.getChildren().add(userLabel);

        Button saveBtn = new Button("Save Event");
        saveBtn.setOnAction(e -> handleSaveEvent());

        Button refreshBtn = new Button("Refresh Events");
        refreshBtn.setOnAction(e -> refreshEventList());

        Button deleteBtn = new Button("Delete Selected Event");
        deleteBtn.setOnAction(e -> handleDeleteSelectedEvent());

        Button logoutBtn = new Button("Logout");
        logoutBtn.setOnAction(e -> handleLogout());

        Button fetchWeatherBtn = new Button("Fetch Weather");
        fetchWeatherBtn.setOnAction(e -> fetchWeatherForecast());

        Button findPlacesBtn = new Button("Find Places");
        findPlacesBtn.setOnAction(e -> fetchPlaceSuggestions());

        Button saveGoalBtn = new Button("Save Goal");
        saveGoalBtn.setOnAction(e -> handleSaveGoal());

        HBox eventActions = new HBox(8, saveBtn, refreshBtn, deleteBtn, logoutBtn);
        eventActions.getStyleClass().add("action-row");
        HBox.setHgrow(saveBtn, Priority.ALWAYS);
        HBox.setHgrow(refreshBtn, Priority.ALWAYS);
        HBox.setHgrow(deleteBtn, Priority.ALWAYS);
        HBox.setHgrow(logoutBtn, Priority.ALWAYS);
        saveBtn.setMaxWidth(Double.MAX_VALUE);
        refreshBtn.setMaxWidth(Double.MAX_VALUE);
        deleteBtn.setMaxWidth(Double.MAX_VALUE);
        logoutBtn.setMaxWidth(Double.MAX_VALUE);

        HBox weatherActions = new HBox(8, findPlacesBtn, fetchWeatherBtn);
        weatherActions.getStyleClass().add("action-row");
        HBox.setHgrow(findPlacesBtn, Priority.ALWAYS);
        HBox.setHgrow(fetchWeatherBtn, Priority.ALWAYS);
        findPlacesBtn.setMaxWidth(Double.MAX_VALUE);
        fetchWeatherBtn.setMaxWidth(Double.MAX_VALUE);

        HBox goalActions = new HBox(8, saveGoalBtn);
        goalActions.getStyleClass().add("action-row");
        HBox.setHgrow(saveGoalBtn, Priority.ALWAYS);
        saveGoalBtn.setMaxWidth(Double.MAX_VALUE);

        VBox eventDetailsContent = new VBox(8,
            new Label("Title"), titleField,
            new Label("Description"), descriptionField,
            new Label("Date"), datePicker,
            new Label("Start"), startTimeBox,
            new Label("End"), endTimeBox,
            recurringCheck,
            new Label("Event actions"), eventActions
        );

        Label customReminderHint = new Label("Use custom reminder if you want a warning before the event starts.");
        customReminderHint.setStyle(settings.isDarkTheme()
            ? "-fx-text-fill: #cbd5e1; -fx-font-size: 11px;"
            : "-fx-text-fill: #475569; -fx-font-size: 11px;");

        Label reminderMinutesLabel = new Label("Minutes before start");
        Label snoozeMinutesLabel = new Label("Snooze minutes");

        Runnable syncCustomReminderControls = () -> {
            boolean customEnabled = reminderCheck.isSelected();
            reminderMinutesField.setDisable(!customEnabled);
            snoozeMinutesField.setDisable(!customEnabled);
            reminderMinutesLabel.setDisable(!customEnabled);
            snoozeMinutesLabel.setDisable(!customEnabled);
            customReminderHint.setDisable(!customEnabled);
            customReminderHint.setOpacity(customEnabled ? 1.0 : 0.75);
        };

        reminderCheck.selectedProperty().addListener((obs, oldValue, newValue) -> syncCustomReminderControls.run());
        syncCustomReminderControls.run();

        VBox reminderContent = new VBox(8,
            autoReminderCheck,
            reminderCheck,
            customReminderHint,
            reminderMinutesLabel, reminderMinutesField,
            snoozeMinutesLabel, snoozeMinutesField
        );

        VBox weatherContent = new VBox(8,
            new Label("Temperature unit"), weatherUnitBox,
            new Label("Weather city/place"), weatherPlaceBox,
            new Label("Weather latitude"), weatherLatitudeField,
            new Label("Weather longitude"), weatherLongitudeField,
            new Label("Weather actions"), weatherActions
        );

        VBox goalContent = new VBox(8,
            new Label("Goal name"), goalNameField,
            new Label("Current value"), goalCurrentField,
            new Label("Target value"), goalTargetField,
            new Label("Goal actions"), goalActions,
            goalStatusLabel
        );

        VBox displayContent = new VBox(8,
            new Label("Theme"), themeBox,
            new Label("Time format"), timeFormatBox,
            sidebarToggle,
            formStatusLabel
        );

        TitledPane eventPane = new TitledPane("Event Details", eventDetailsContent);
        TitledPane reminderPane = new TitledPane("Reminder Settings", reminderContent);
        TitledPane weatherPane = new TitledPane("Weather Settings", weatherContent);
        TitledPane goalPane = new TitledPane("Goal Settings", goalContent);
        TitledPane displayPane = new TitledPane("Display Settings", displayContent);

        Accordion formAccordion = new Accordion(eventPane, reminderPane, weatherPane, goalPane, displayPane);
        formAccordion.getStyleClass().add("form-accordion");
        formAccordion.expandedPaneProperty().addListener((obs, oldPane, newPane) -> {
            if (newPane == eventPane) {
                settings.changeExpandedSection(Settings.SECTION_EVENT);
            } else if (newPane == reminderPane) {
                settings.changeExpandedSection(Settings.SECTION_REMINDER);
            } else if (newPane == weatherPane) {
                settings.changeExpandedSection(Settings.SECTION_WEATHER);
            } else if (newPane == goalPane) {
                settings.changeExpandedSection(Settings.SECTION_GOAL);
            } else if (newPane == displayPane) {
                settings.changeExpandedSection(Settings.SECTION_DISPLAY);
            }
        });

        String expandedSection = settings.getExpandedSection();
        if (Settings.SECTION_REMINDER.equals(expandedSection)) {
            formAccordion.setExpandedPane(reminderPane);
        } else if (Settings.SECTION_WEATHER.equals(expandedSection)) {
            formAccordion.setExpandedPane(weatherPane);
        } else if (Settings.SECTION_GOAL.equals(expandedSection)) {
            formAccordion.setExpandedPane(goalPane);
        } else if (Settings.SECTION_DISPLAY.equals(expandedSection)) {
            formAccordion.setExpandedPane(displayPane);
        } else {
            formAccordion.setExpandedPane(eventPane);
        }

        form.getChildren().add(formAccordion);

        ScrollPane formScrollPane = new ScrollPane(form);
        formScrollPane.getStyleClass().add("form-scroll");
        formScrollPane.setFitToWidth(true);
        formScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        formScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);

        sideTabs = buildSideTabs();
        calendarPanel = buildCalendarPanel();
        startReminderPolling();

        setWeatherStatus("Click Fetch Weather to load the forecast.");

        contentSplit = new SplitPane();
        contentSplit.getStyleClass().add("center-split");
        contentSplit.getItems().addAll(calendarPanel, sideTabs);
        contentSplit.setDividerPositions(settings.getSidePanelDivider());
        if (!contentSplit.getDividers().isEmpty()) {
            contentSplit.getDividers().get(0).positionProperty().addListener((obs, oldPos, newPos) ->
                settings.changeSidePanelDivider(newPos.doubleValue())
            );
        }

        eventsRoot = new BorderPane();
        eventsRoot.getStyleClass().add("events-root");
        eventsRoot.setPadding(new Insets(14));
        eventsRoot.setLeft(formScrollPane);
        eventsRoot.setCenter(contentSplit);
        BorderPane.setMargin(contentSplit, new Insets(0, 0, 0, 16));

        currentEventsScene = new Scene(eventsRoot);
        applySceneStyles(currentEventsScene);
        applyThemeToCurrentScene();
        updateResponsiveLayout();
        return currentEventsScene;
    }

    private ComboBox<String> buildTimePicker(String selected) {
        ComboBox<String> box = new ComboBox<>();
        for (int hour = 0; hour < 24; hour++) {
            for (int minute = 0; minute < 60; minute += 30) {
                LocalTime time = LocalTime.of(hour, minute);
                box.getItems().add(time.format(DateTimeFormatter.ofPattern("h:mm a")));
            }
        }
        box.setEditable(true);
        box.setValue(selected);
        return box;
    }

    private DateTimeFormatter activeEventTimeFormatter() {
        if (Settings.TIME_FORMAT_24.equals(settings.getTimeFormat())) {
            return DateTimeFormatter.ofPattern("HH:mm");
        }
        return DateTimeFormatter.ofPattern("h:mm a");
    }

    private TabPane buildSideTabs() {
        eventListTitle = new Label("Events");
        eventListTitle.getStyleClass().add("section-title");
        eventsListView = new ListView<>();
        eventsListView.setPlaceholder(new Label("No events yet."));
        eventsListView.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                int index = eventsListView.getSelectionModel().getSelectedIndex();
                if (index >= 0 && index < displayedEvents.size()) {
                    showEventDetails(displayedEvents.get(index));
                }
            }
        });
        VBox eventsPane = new VBox(8, eventListTitle, eventsListView);
        VBox.setVgrow(eventsListView, Priority.ALWAYS);
        eventsPane.setPadding(new Insets(8));

        Label reminderTitle = new Label("Reminders");
        reminderTitle.getStyleClass().add("section-title");
        remindersListView = new ListView<>();
        remindersListView.setPlaceholder(new Label("No reminders configured."));
        VBox remindersPane = new VBox(8, reminderTitle, remindersListView);
        VBox.setVgrow(remindersListView, Priority.ALWAYS);
        remindersPane.setPadding(new Insets(8));

        weatherHeaderLabel = new Label("Weather");
        weatherHeaderLabel.getStyleClass().add("section-title");
        weatherCardsBox = new VBox(8);
        weatherCardsBox.setPadding(new Insets(4));
        weatherScrollPane = new ScrollPane(weatherCardsBox);
        weatherScrollPane.setFitToWidth(true);
        weatherScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        VBox weatherPane = new VBox(8, weatherHeaderLabel, weatherScrollPane);
        VBox.setVgrow(weatherScrollPane, Priority.ALWAYS);
        weatherPane.setPadding(new Insets(8));

        Label goalsTitle = new Label("Goals");
        goalsTitle.getStyleClass().add("section-title");
        VBox goalsBox = new VBox(8);
        goalsBox.setPadding(new Insets(6));
        ScrollPane goalsScroll = new ScrollPane(goalsBox);
        goalsScroll.setFitToWidth(true);
        goalsScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        goalsScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        VBox goalsPane = new VBox(8, goalsTitle, goalsScroll);
        VBox.setVgrow(goalsScroll, Priority.ALWAYS);
        goalsPane.setPadding(new Insets(8));
        this.goalsContainer = goalsBox;

        Tab eventsTab = new Tab("Events", eventsPane);
        eventsTab.setClosable(false);
        Tab remindersTab = new Tab("Reminders", remindersPane);
        remindersTab.setClosable(false);
        Tab weatherTab = new Tab("Weather", weatherPane);
        weatherTab.setClosable(false);
        Tab goalsTab = new Tab("Goals", goalsPane);
        goalsTab.setClosable(false);

        TabPane tabs = new TabPane(eventsTab, remindersTab, weatherTab, goalsTab);
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.setPrefWidth(360);
        return tabs;
    }

    private void updateResponsiveLayout() {
        if (eventsRoot == null || sideTabs == null || contentSplit == null || calendarPanel == null) {
            return;
        }

        if (!settings.isSideBarOpen()) {
            contentSplit.getItems().setAll(calendarPanel);
            return;
        }

        sideTabs.setPrefHeight(Region.USE_COMPUTED_SIZE);
        sideTabs.setPrefWidth(360);
        contentSplit.getItems().setAll(calendarPanel, sideTabs);
        contentSplit.setDividerPositions(settings.getSidePanelDivider());
    }

    private void applySceneStyles(Scene scene) {
        java.net.URL css = getClass().getResource("/styles/app.css");
        if (css != null) {
            scene.getStylesheets().setAll(css.toExternalForm());
        }
    }

    private void applyThemeToCurrentScene() {
        if (currentEventsScene == null) {
            return;
        }
        currentEventsScene.getRoot().getStyleClass().removeAll("theme-light", "theme-dark");
        currentEventsScene.getRoot().getStyleClass().add(settings.isDarkTheme() ? "theme-dark" : "theme-light");
    }

    private VBox buildCalendarPanel() {
        monthLabel = new Label();
        monthLabel.getStyleClass().add("month-label");

        Button previousMonthBtn = new Button("<");
        previousMonthBtn.setOnAction(e -> {
            visibleMonth = visibleMonth.minusMonths(1);
            rebuildMonthGrid();
        });

        Button nextMonthBtn = new Button(">");
        nextMonthBtn.setOnAction(e -> {
            visibleMonth = visibleMonth.plusMonths(1);
            rebuildMonthGrid();
        });

        Button clearFilterBtn = new Button("Clear Day Filter");
        clearFilterBtn.setOnAction(e -> clearSelectedDate());

        HBox monthHeader = new HBox(10, previousMonthBtn, monthLabel, nextMonthBtn, clearFilterBtn);
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

        // Reminder summary under the calendar so users don't need to switch tabs.
        Label monthRemindersTitle = new Label("Upcoming Reminders");
        monthRemindersTitle.getStyleClass().add("section-title");
        monthRemindersListView = new ListView<>();
        monthRemindersListView.setPlaceholder(new Label("No reminders yet."));
        monthRemindersListView.setPrefHeight(150);
        monthRemindersListView.setMaxHeight(180);

        VBox panel = new VBox(10, monthHeader, monthGrid, monthRemindersTitle, monthRemindersListView);
        VBox.setVgrow(monthGrid, Priority.ALWAYS);
        rebuildMonthGrid();
        refreshRemindersArea();
        return panel;
    }

    private void rebuildMonthGrid() {
        monthGrid.getChildren().clear();
        monthLabel.setText(visibleMonth.getMonth() + " " + visibleMonth.getYear());
        // Rebuild the full month grid every time the month, selection, or event data changes.

        String[] dayHeaders = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
        for (int col = 0; col < dayHeaders.length; col++) {
            Label day = new Label(dayHeaders[col]);
            day.setMaxWidth(Double.MAX_VALUE);
            day.setAlignment(Pos.CENTER);
            day.getStyleClass().add("day-header");
            monthGrid.add(day, col, 0);
        }

        Map<LocalDate, List<Event>> eventsByDate = new HashMap<>();
        if (currentUser != null) {
            for (Event event : allEvents) {
                eventsByDate.computeIfAbsent(event.getDate(), k -> new ArrayList<>()).add(event);
            }
        }

        LocalDate firstOfMonth = visibleMonth.atDay(1);
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

                        // Show first few titles in the day cell so the month view is informative at a glance.
                        int maxTitlesInCell = Math.min(2, count);
                        for (int i = 0; i < maxTitlesInCell; i++) {
                            Event event = eventsOnDate.get(i);
                            Button eventTitle = new Button(event.getTitle());
                            eventTitle.setMaxWidth(Double.MAX_VALUE);
                            eventTitle.setAlignment(Pos.CENTER_LEFT);
                            eventTitle.getStyleClass().add("day-event-button");
                            eventTitle.setOnAction(e -> showEventDetails(event));
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
                        selectDateForEventCreation(date);
                        showQuickAddEventDialog(date);
                    });

                    dayCell.setOnMouseClicked(e -> {
                        if (e.getClickCount() >= 2) {
                            singleClickDelay.stop();
                            selectDateForEventCreation(date);
                            showEventsForDate(date, eventsOnDate);
                            return;
                        }

                        if (e.getClickCount() == 1) {
                            singleClickDelay.playFromStart();
                        }
                    });

                    if (date.equals(LocalDate.now())) {
                        dayCell.setStyle(dayCell.getStyle() + todayBorderStyle());
                    }

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
            if (selectedDate != null && date.equals(selectedDate)) {
                dayCell.setStyle(selectedDayCellStyle());
            } else if (date.equals(LocalDate.now())) {
                dayCell.setStyle(normalStyle + todayHoverBorderStyle());
            } else {
                dayCell.setStyle(hoverDayCellStyle(hasEvents));
            }
        });

        dayCell.setOnMouseExited(e -> {
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

    private void selectDateForEventCreation(LocalDate date) {
        selectedDate = date;

        if (datePicker != null) {
            datePicker.setValue(date);
        }

        if (formStatusLabel != null) {
            formStatusLabel.setText("Selected " + date + ". Enter title/details, then click Save Event.");
        }

        refreshEventList();
    }

    private void showQuickAddEventDialog(LocalDate date) {
        if (currentUser == null) {
            authStatusLabel.setText("Please log in first.");
            return;
        }

        // Lightweight event dialog launched from a single click on a day cell.
        Dialog<QuickAddEventInput> dialog = new Dialog<>();
        dialog.initOwner(stage);
        dialog.setTitle("Quick Add Event");
        dialog.setHeaderText("Create event for " + date);

        ButtonType saveType = new ButtonType("Save Event", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);

        TextField quickTitleField = new TextField();
        quickTitleField.setPromptText("Event title");
        TextField quickDescriptionField = new TextField();
        quickDescriptionField.setPromptText("Description (optional)");

        String defaultStart = selectedTimeValue(startTimeBox);
        String defaultEnd = selectedTimeValue(endTimeBox);
        ComboBox<String> quickStartBox = buildTimePicker(defaultStart.isBlank() ? "9:00 AM" : defaultStart);
        ComboBox<String> quickEndBox = buildTimePicker(defaultEnd.isBlank() ? "10:00 AM" : defaultEnd);
        bindEndTimeAfterStart(quickStartBox, quickEndBox);

        CheckBox quickRecurringCheck = new CheckBox("Recurring");
        quickRecurringCheck.setSelected(recurringCheck != null && recurringCheck.isSelected());
        // Inherit current reminder preference but still allow one-off override.
        CheckBox quickAutoReminderCheck = new CheckBox("Automatic reminder at start time");
        quickAutoReminderCheck.setSelected(autoReminderCheck == null || autoReminderCheck.isSelected());

        GridPane content = new GridPane();
        content.setHgap(8);
        content.setVgap(8);
        content.add(new Label("Title"), 0, 0);
        content.add(quickTitleField, 1, 0);
        content.add(new Label("Description"), 0, 1);
        content.add(quickDescriptionField, 1, 1);
        content.add(new Label("Start"), 0, 2);
        content.add(quickStartBox, 1, 2);
        content.add(new Label("End"), 0, 3);
        content.add(quickEndBox, 1, 3);
        content.add(quickRecurringCheck, 1, 4);
        content.add(quickAutoReminderCheck, 1, 5);

        dialog.getDialogPane().setContent(content);

        dialog.setResultConverter(buttonType -> {
            if (buttonType != saveType) {
                return null;
            }

            String title = quickTitleField.getText() == null ? "" : quickTitleField.getText().trim();
            String description = quickDescriptionField.getText() == null ? "" : quickDescriptionField.getText().trim();

            return new QuickAddEventInput(
                title,
                description,
                selectedTimeValue(quickStartBox),
                selectedTimeValue(quickEndBox),
                quickRecurringCheck.isSelected(),
                quickAutoReminderCheck.isSelected()
            );
        });

        Optional<QuickAddEventInput> result = dialog.showAndWait();
        if (result.isEmpty()) {
            return;
        }

        try {
            QuickAddEventInput input = result.get();
            Event saved = EventService.createEvent(
                currentUser,
                input.title,
                input.description,
                date.toString(),
                input.startTime,
                input.endTime,
                input.recurring
            );

            if (input.autoReminder) {
                createAutomaticStartTimeReminder(saved);
            }
            CsvStorage.saveRemindersForUser(currentUser.getAccountID(), reminders);

            if (titleField != null) {
                titleField.setText(saved.getTitle());
            }
            if (descriptionField != null) {
                descriptionField.setText(saved.getDescription());
            }
            if (datePicker != null) {
                datePicker.setValue(date);
            }

            formStatusLabel.setText("Quick-saved event #" + saved.getId() + " for " + date + ".");
            refreshEventList();
            userLabel.setText("Logged in as: " + currentUser.getUsername() + " (ID " + currentUser.getAccountID() + ") - Saved event #" + saved.getId());
        } catch (IllegalArgumentException ex) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Could Not Save Event");
            alert.setHeaderText("Quick add failed");
            alert.setContentText(ex.getMessage());
            alert.showAndWait();
        }
    }

    private void showEventDetails(Event event) {
        // This popup is the detailed event view opened from a title button or the day list.
        DateTimeFormatter fmt = activeEventTimeFormatter();
        String details = "Title: " + event.getTitle() + "\n"
            + "Description: " + event.getDescription() + "\n"
            + "Date: " + event.getDate() + "\n"
            + "Time: " + event.getStartTime().format(fmt) + " - " + event.getEndTime().format(fmt) + "\n"
            + "Recurring: " + event.isRecurring();

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Event Details");
        alert.setHeaderText("Event #" + event.getId());
        alert.setContentText(details);
        alert.showAndWait();
    }

    private void showEventsForDate(LocalDate date, List<Event> events) {
        // This popup lists all events on a selected date and keeps each entry clickable.
        Stage popup = new Stage();
        popup.initOwner(stage);
        popup.initModality(Modality.WINDOW_MODAL);
        popup.setTitle("Events for " + date);

        VBox content = new VBox(10);
        content.setPadding(new Insets(16));

        Label header = new Label("Events on " + date);
        header.getStyleClass().add("section-title");
        content.getChildren().add(header);

        if (events.isEmpty()) {
            content.getChildren().add(new Label("No events on this date."));
        } else {
            for (Event event : events) {
                Button eventButton = new Button(formatEventSummary(event));
                eventButton.setMaxWidth(Double.MAX_VALUE);
                eventButton.setPrefWidth(Region.USE_COMPUTED_SIZE);
                eventButton.setAlignment(Pos.CENTER_LEFT);
                eventButton.getStyleClass().add("day-event-button");
                eventButton.setOnAction(e -> showEventDetails(event));
                content.getChildren().add(eventButton);
            }
        }

        Button closeBtn = new Button("Close");
        closeBtn.setOnAction(e -> popup.close());
        content.getChildren().add(closeBtn);

        popup.setScene(new Scene(content, 420, 300));
        popup.showAndWait();
    }

    private String formatEventSummary(Event event) {
        DateTimeFormatter fmt = activeEventTimeFormatter();
        return "#" + event.getId() + " | "
            + event.getTitle() + " | "
            + event.getStartTime().format(fmt) + " - "
            + event.getEndTime().format(fmt)
            + (event.isRecurring() ? " | recurring" : "");
    }

    private void handleCreateAccount() {
        String username = usernameField.getText();
        String password = passwordField.getText();

        try {
            UserAccount user = AuthService.createAccount(username, password);
            authStatusLabel.setText("Account created. ID: " + user.getAccountID() + ". You can now login.");
        } catch (IllegalArgumentException ex) {
            authStatusLabel.setText(ex.getMessage());
        }
    }

    private void handleLogin() {
        String username = usernameField.getText();
        String password = passwordField.getText();
        // Per-login control for session persistence.
        boolean rememberMe = rememberMeCheck != null && rememberMeCheck.isSelected();

        try {
            UserAccount authenticatedUser = AuthService.login(username, password);
            openUserSession(authenticatedUser, rememberMe);
            if (!rememberMe) {
                CsvStorage.clearCurrentSession();
            }
        } catch (IllegalArgumentException ex) {
            authStatusLabel.setText(ex.getMessage());
        }
    }

    private boolean tryRestorePersistedSession() {
        // Uses persisted account ID from session.csv to skip manual login.
        Integer accountId = CsvStorage.loadCurrentSessionAccountId();
        if (accountId == null) {
            return false;
        }

        UserAccount restoredUser = CsvStorage.findUserByAccountId(accountId);
        if (restoredUser == null) {
            CsvStorage.clearCurrentSession();
            return false;
        }

        openUserSession(restoredUser, false);
        return true;
    }

    private void openUserSession(UserAccount user, boolean persistSession) {
        // Shared login path used by both manual login and session restore.
        currentUser = user;
        selectedDate = null;
        reloadEventsCache();
        stage.setScene(buildEventsScene());

        reminders.clear();
        reminders.addAll(CsvStorage.loadRemindersForUser(currentUser.getAccountID()));
        goals.clear();
        goals.addAll(CsvStorage.loadGoalsForUser(currentUser.getAccountID()));

        userLabel.setText("Logged in as: " + currentUser.getUsername() + " (ID " + currentUser.getAccountID() + ")");
        refreshEventList();
        refreshGoalsArea();

        if (persistSession) {
            CsvStorage.saveCurrentSession(currentUser.getAccountID());
        }
    }

    private void handleSaveEvent() {
        if (currentUser == null) {
            authStatusLabel.setText("No active user session.");
            return;
        }

        try {
            Event saved = EventService.createEvent(
                currentUser,
                titleField.getText(),
                descriptionField.getText(),
                datePicker.getValue() == null ? "" : datePicker.getValue().toString(),
                selectedTimeValue(startTimeBox),
                selectedTimeValue(endTimeBox),
                recurringCheck.isSelected()
            );

            // Automatic reminder is optional and can be toggled per event.
            if (autoReminderCheck.isSelected()) {
                createAutomaticStartTimeReminder(saved);
            }
            // Optional user-defined reminder remains available in the form.
            createReminderIfRequested(saved);
            CsvStorage.saveRemindersForUser(currentUser.getAccountID(), reminders);
            titleField.clear();
            descriptionField.clear();
            formStatusLabel.setText("Saved event #" + saved.getId());
            refreshEventList();
            rebuildMonthGrid();
            refreshRemindersArea();
            userLabel.setText("Logged in as: " + currentUser.getUsername() + " (ID " + currentUser.getAccountID() + ") - Saved event #" + saved.getId());
        } catch (IllegalArgumentException ex) {
            formStatusLabel.setText(ex.getMessage());
        }
    }

    private void handleDeleteSelectedEvent() {
        if (currentUser == null) {
            authStatusLabel.setText("No active user session.");
            return;
        }

        int selectedIndex = eventsListView == null ? -1 : eventsListView.getSelectionModel().getSelectedIndex();
        if (selectedIndex < 0 || selectedIndex >= displayedEvents.size()) {
            formStatusLabel.setText("Select an event from the Events list first.");
            return;
        }

        Event selectedEvent = displayedEvents.get(selectedIndex);
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Delete Event");
        confirm.setHeaderText("Delete \"" + selectedEvent.getTitle() + "\"?");
        confirm.setContentText("This will remove the event and its reminders.");

        Optional<ButtonType> decision = confirm.showAndWait();
        if (decision.isEmpty() || decision.get() != ButtonType.OK) {
            return;
        }

        // Keep reminder storage consistent by removing reminders tied to deleted event.
        CsvStorage.deleteEventForUser(currentUser.getAccountID(), selectedEvent.getId());
        reminders.removeIf(reminder -> reminder.getEventId() == selectedEvent.getId());
        CsvStorage.saveRemindersForUser(currentUser.getAccountID(), reminders);

        formStatusLabel.setText("Deleted event #" + selectedEvent.getId());
        refreshEventList();
    }

    private void handleSaveGoal() {
        if (currentUser == null) {
            authStatusLabel.setText("No active user session.");
            return;
        }

        String name = goalNameField.getText() == null ? "" : goalNameField.getText().trim();
        if (name.isBlank()) {
            goalStatusLabel.setText("Goal name is required.");
            return;
        }

        double currentValue;
        double targetValue;
        try {
            currentValue = Double.parseDouble(goalCurrentField.getText().trim());
            targetValue = Double.parseDouble(goalTargetField.getText().trim());
        } catch (NumberFormatException ex) {
            goalStatusLabel.setText("Current and target values must be numbers.");
            return;
        }

        if (targetValue <= 0) {
            goalStatusLabel.setText("Target value must be greater than 0.");
            return;
        }
        if (currentValue < 0) {
            goalStatusLabel.setText("Current value cannot be negative.");
            return;
        }

        Goal existing = null;
        for (Goal goal : goals) {
            if (goal.getName().equalsIgnoreCase(name)) {
                existing = goal;
                break;
            }
        }

        if (existing == null) {
            goals.add(new Goal(0, name, currentValue, targetValue));
        } else {
            existing.setCurrentValue(currentValue);
            existing.setTargetValue(targetValue);
        }

        CsvStorage.saveGoalsForUser(currentUser.getAccountID(), goals);
        refreshGoalsArea();
        goalNameField.clear();
        goalCurrentField.setText("0");
        goalTargetField.setText("100");
        goalStatusLabel.setText("Goal saved.");
    }

    private String selectedTimeValue(ComboBox<String> box) {
        String value = box.getValue();
        if (value == null || value.isBlank()) {
            value = box.getEditor().getText();
        }
        return value == null ? "" : value.trim();
    }

    private void bindEndTimeAfterStart(ComboBox<String> startBox, ComboBox<String> endBox) {
        // Keep end time in sync whenever start time is changed by picker or typed input.
        startBox.setOnAction(e -> ensureEndAfterStart(startBox, endBox));
        startBox.getEditor().focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (!isFocused) {
                ensureEndAfterStart(startBox, endBox);
            }
        });
        startBox.valueProperty().addListener((obs, oldValue, newValue) -> ensureEndAfterStart(startBox, endBox));
    }

    private void ensureEndAfterStart(ComboBox<String> startBox, ComboBox<String> endBox) {
        LocalTime start = parseFlexibleTime(selectedTimeValue(startBox));
        if (start == null) {
            return;
        }

        LocalTime end = parseFlexibleTime(selectedTimeValue(endBox));
        if (end != null && end.isAfter(start)) {
            return;
        }

        // Default duration is 30 minutes when end is missing or not after start.
        LocalTime suggestedEnd = start.equals(LocalTime.of(23, 30))
            ? LocalTime.of(23, 59)
            : start.plusMinutes(30);
        endBox.setValue(suggestedEnd.format(DateTimeFormatter.ofPattern("h:mm a")));
    }

    private LocalTime parseFlexibleTime(String raw) {
        // Accept both 24-hour input (HH:mm) and 12-hour input (h:mm AM/PM).
        if (raw == null || raw.isBlank()) {
            return null;
        }

        String normalized = raw.trim();
        try {
            return LocalTime.parse(normalized);
        } catch (DateTimeParseException ignored) {
            // Try 12-hour time with AM/PM next.
        }

        try {
            return LocalTime.parse(normalized.toUpperCase(Locale.ENGLISH), DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH));
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private void refreshEventList() {
        if (eventsListView == null || eventListTitle == null) {
            return;
        }

        if (currentUser == null) {
            eventListTitle.setText("Events");
            eventsListView.getItems().clear();
            displayedEvents = new ArrayList<>();
            return;
        }

        reloadEventsCache();
        List<Event> events = getDisplayedEvents();
        displayedEvents = new ArrayList<>(events);

        eventListTitle.setText(selectedDate == null
            ? "Events for " + currentUser.getUsername()
            : "Events for " + currentUser.getUsername() + " on " + selectedDate);

        eventsListView.getItems().clear();
        if (events.isEmpty()) {
            eventsListView.getItems().add(selectedDate == null ? "No events yet." : "No events on this date.");
        } else {
            DateTimeFormatter fmt = activeEventTimeFormatter();
            for (Event event : events) {
                eventsListView.getItems().add("#" + event.getId()
                    + "  " + event.getDate()
                    + "  " + event.getStartTime().format(fmt)
                    + " - " + event.getEndTime().format(fmt)
                    + "  " + event.getTitle()
                    + (event.isRecurring() ? " (recurring)" : ""));
            }
        }

        rebuildMonthGrid();
        refreshRemindersArea();
    }

    private void refreshGoalsArea() {
        if (goalsContainer == null) {
            return;
        }

        goalsContainer.getChildren().clear();

        if (currentUser == null) {
            goalsContainer.getChildren().add(new Label("Login to manage goals."));
            return;
        }

        if (goals.isEmpty()) {
            goalsContainer.getChildren().add(new Label("No goals yet. Add one from Goal Settings."));
            return;
        }

        for (Goal goal : goals) {
            goalsContainer.getChildren().add(buildGoalCard(goal));
        }
    }

    private VBox buildGoalCard(Goal goal) {
        Label name = new Label(goal.getName());
        name.getStyleClass().add("section-title");

        javafx.scene.control.ProgressBar bar = new javafx.scene.control.ProgressBar(goal.progressFraction());
        bar.setMaxWidth(Double.MAX_VALUE);

        Label details = new Label(goal.progressLabel());

        VBox card = new VBox(6, name, bar, details);
        card.getStyleClass().add("weather-card");
        card.setPadding(new Insets(10));
        return card;
    }

    private void handleLogout() {
        if (currentUser != null) {
            CsvStorage.saveRemindersForUser(currentUser.getAccountID(), reminders);
            CsvStorage.saveGoalsForUser(currentUser.getAccountID(), goals);
        }
        // Logout always clears persisted session regardless of Remember me default.
        CsvStorage.clearCurrentSession();
        currentUser = null;
        selectedDate = null;
        reminders.clear();
        goals.clear();
        allEvents.clear();
        eventsById.clear();
        currentWeatherLocationLabel = "";
        currentWeatherForecast = new ArrayList<>();
        stopReminderPolling();
        stage.setScene(buildAuthScene());
        authStatusLabel.setText("Logged out.");
    }

    private void fetchWeatherForecast() {
        String placeQuery = weatherPlaceBox.getEditor().getText();
        double fallbackLatitude = parseDoubleOrDefault(weatherLatitudeField.getText(), 40.7128);
        double fallbackLongitude = parseDoubleOrDefault(weatherLongitudeField.getText(), -74.0060);

        setWeatherStatus("Loading forecast...");

        Task<WeatherForecastResult> weatherTask = new Task<>() {
            @Override
            protected WeatherForecastResult call() {
                WeatherLocation location = resolveWeatherLocation(placeQuery, fallbackLatitude, fallbackLongitude);
                weatherService.setLocation(location.latitude, location.longitude);
                weatherService.getWeather();
                return new WeatherForecastResult(location, weatherService.getHourlyForecast());
            }
        };

        weatherTask.setOnSucceeded(e -> {
            WeatherForecastResult result = weatherTask.getValue();
            weatherLatitudeField.setText(String.format("%.4f", result.location.latitude));
            weatherLongitudeField.setText(String.format("%.4f", result.location.longitude));
            weatherPlaceBox.getEditor().setText(result.location.label);
            if (!weatherPlaceBox.getItems().contains(result.location.label)) {
                weatherPlaceBox.getItems().add(0, result.location.label);
            }
            weatherLocationLookup.put(result.location.label, result.location);
            currentWeatherLocationLabel = result.location.label;
            currentWeatherLatitude = result.location.latitude;
            currentWeatherLongitude = result.location.longitude;
            currentWeatherForecast = new ArrayList<>(result.forecast);
            renderWeatherForecast(result.location.label, result.location.latitude, result.location.longitude, result.forecast);
        });
        weatherTask.setOnFailed(e -> setWeatherStatus("Unable to load forecast: " + weatherTask.getException().getMessage()));

        Thread weatherThread = new Thread(weatherTask, "weather-fetch");
        weatherThread.setDaemon(true);
        weatherThread.start();
    }

    private void renderWeatherForecast(String locationLabel, double latitude, double longitude, List<WeatherService.ForecastEntry> forecast) {
        weatherHeaderLabel.setText("Weather (" + weatherUnitSuffix() + ") - " + locationLabel);

        if (forecast == null || forecast.isEmpty()) {
            setWeatherStatus("No forecast data returned.");
            return;
        }

        List<WeatherService.ForecastEntry> todayForecast = forecastForDate(forecast, LocalDate.now());
        List<WeatherService.ForecastEntry> entriesToShow = todayForecast.isEmpty() ? forecast : todayForecast;

        weatherCardsBox.getChildren().clear();

        Label meta = new Label("Coords: " + String.format("%.4f", latitude) + ", " + String.format("%.4f", longitude)
            + "  |  " + (todayForecast.isEmpty() ? "Upcoming" : "Today") + " forecast"
            + "  |  Unit: " + (isFahrenheitWeatherUnit() ? "°F" : "°C"));
        meta.setStyle(settings.isDarkTheme()
            ? "-fx-text-fill: #cbd5e1; -fx-font-size: 11px;"
            : "-fx-text-fill: #475569; -fx-font-size: 11px;");
        weatherCardsBox.getChildren().add(meta);
        weatherCardsBox.getChildren().add(buildWeatherLegend());

        int limit = Math.min(8, entriesToShow.size());
        for (int i = 0; i < limit; i++) {
            WeatherService.ForecastEntry entry = entriesToShow.get(i);
            weatherCardsBox.getChildren().add(buildWeatherCard(entry));
        }

        if (entriesToShow.size() > limit) {
            Label more = new Label("Showing first " + limit + " hours.");
            more.setStyle(settings.isDarkTheme()
                ? "-fx-text-fill: #94a3b8; -fx-font-size: 11px;"
                : "-fx-text-fill: #64748b; -fx-font-size: 11px;");
            weatherCardsBox.getChildren().add(more);
        }
    }

    private HBox buildWeatherLegend() {
        HBox legend = new HBox(8,
            buildLegendItem("☀ Clear", "#fff8db", "#f2d37a"),
            buildLegendItem("☁ Cloud/Fog", "#eef2f7", "#c7d2e0"),
            buildLegendItem("🌧 Rain", "#e6f2ff", "#8ec0f4"),
            buildLegendItem("❄ Snow", "#edf5ff", "#bcd7f5"),
            buildLegendItem("⛈ Storm", "#fff1d6", "#f4b860")
        );
        legend.setAlignment(Pos.CENTER_LEFT);
        return legend;
    }

    private Label buildLegendItem(String text, String bgColor, String borderColor) {
        String textColor = settings.isDarkTheme() ? "#e2e8f0" : "#334155";
        Label chip = new Label(text);
        chip.setStyle("-fx-font-size: 10px; -fx-padding: 3 8 3 8; -fx-background-color: " + bgColor
            + "; -fx-border-color: " + borderColor
            + "; -fx-border-radius: 999; -fx-background-radius: 999; -fx-text-fill: " + textColor + ";");
        return chip;
    }

    private void setWeatherStatus(String message) {
        weatherHeaderLabel.setText("Weather (" + weatherUnitSuffix() + ")");
        weatherCardsBox.getChildren().setAll(buildWeatherStatusCard(message));
    }

    private HBox buildWeatherStatusCard(String message) {
        Label icon = new Label("i");
        icon.setStyle(settings.isDarkTheme()
            ? "-fx-font-size: 16px; -fx-text-fill: #e2e8f0;"
            : "-fx-font-size: 16px; -fx-text-fill: #0f172a;");
        Label text = new Label(message);
        text.setWrapText(true);
        text.setStyle(settings.isDarkTheme()
            ? "-fx-text-fill: #cbd5e1;"
            : "-fx-text-fill: #334155;");

        HBox card = new HBox(10, icon, text);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setPadding(new Insets(10));
        card.setStyle(settings.isDarkTheme()
            ? "-fx-background-color: #0f172a; -fx-border-color: #334155; -fx-border-radius: 6; -fx-background-radius: 6;"
            : "-fx-background-color: #f8fafc; -fx-border-color: #e2e8f0; -fx-border-radius: 6; -fx-background-radius: 6;");
        HBox.setHgrow(text, Priority.ALWAYS);
        return card;
    }

    private HBox buildWeatherCard(WeatherService.ForecastEntry entry) {
        String condition = shortWeatherLabel(entry.getWeatherCode());
        Label icon = new Label(weatherIcon(entry.getWeatherCode()));
        icon.setStyle("-fx-font-size: 20px; -fx-text-fill: " + weatherAccentColor(entry.getWeatherCode()) + ";");

        Label time = new Label(entry.getTime().toLocalTime().format(activeEventTimeFormatter()));
        time.setStyle(settings.isDarkTheme()
            ? "-fx-font-weight: bold; -fx-text-fill: #f8fafc;"
            : "-fx-font-weight: bold; -fx-text-fill: #0f172a;");

        Label details = new Label(condition + "  |  "
            + formatWeatherTemperature(entry)
            + "  |  rain " + entry.getPrecipitationChance() + "%");
        details.setStyle(settings.isDarkTheme()
            ? "-fx-text-fill: #cbd5e1;"
            : "-fx-text-fill: #334155;");

        VBox textCol = new VBox(3, time, details);
        HBox.setHgrow(textCol, Priority.ALWAYS);

        HBox card = new HBox(10, icon, textCol);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setPadding(new Insets(9));
        card.setStyle(weatherCardStyle(entry.getWeatherCode()));
        return card;
    }

    private void rerenderCurrentWeatherForecast() {
        if (currentWeatherForecast == null || currentWeatherForecast.isEmpty()) {
            return;
        }
        renderWeatherForecast(currentWeatherLocationLabel, currentWeatherLatitude, currentWeatherLongitude, currentWeatherForecast);
    }

    private String formatWeatherTemperature(WeatherService.ForecastEntry entry) {
        double value = entry.getTemperatureCelsius();
        String suffix = "°C";

        if (isFahrenheitWeatherUnit()) {
            value = entry.getTemperatureFahrenheit();
            suffix = "°F";
        }

        return String.format("%.1f%s", value, suffix);
    }

    private boolean isFahrenheitWeatherUnit() {
        return weatherUnitBox != null && "Fahrenheit (°F)".equals(weatherUnitBox.getValue());
    }

    private String weatherUnitSuffix() {
        return isFahrenheitWeatherUnit() ? "°F" : "°C";
    }

    private String weatherCardStyle(int weatherCode) {
        String colors;
        if (settings.isDarkTheme()) {
            colors = switch (weatherCode) {
                case 0, 1, 2 -> "#3a2f14|#8b6b1a";
                case 3, 45, 48 -> "#252a34|#4b5563";
                case 51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82 -> "#162a2d|#0f766e";
                case 71, 73, 75, 77, 85, 86 -> "#1f2937|#64748b";
                case 95, 96, 99 -> "#3b2512|#d97706";
                default -> "#0f172a|#334155";
            };
        } else {
            colors = switch (weatherCode) {
                case 0, 1, 2 -> "#fff8db|#f2d37a";
                case 3, 45, 48 -> "#eef2f7|#c7d2e0";
                case 51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82 -> "#e6f2ff|#8ec0f4";
                case 71, 73, 75, 77, 85, 86 -> "#edf5ff|#bcd7f5";
                case 95, 96, 99 -> "#fff1d6|#f4b860";
                default -> "#f8fafc|#dbe3ee";
            };
        }

        String[] parts = colors.split("\\|");
        return "-fx-background-color: " + parts[0]
            + "; -fx-border-color: " + parts[1]
            + "; -fx-border-radius: 6; -fx-background-radius: 6;";
    }

    private String weatherAccentColor(int weatherCode) {
        if (settings.isDarkTheme()) {
            return switch (weatherCode) {
                case 0, 1, 2 -> "#fcd34d";
                case 3, 45, 48 -> "#cbd5e1";
                case 51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82 -> "#5eead4";
                case 71, 73, 75, 77, 85, 86 -> "#cbd5e1";
                case 95, 96, 99 -> "#fb923c";
                default -> "#cbd5e1";
            };
        }
        return switch (weatherCode) {
            case 0, 1, 2 -> "#b45309";
            case 3, 45, 48 -> "#475569";
            case 51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82 -> "#1d4ed8";
            case 71, 73, 75, 77, 85, 86 -> "#0369a1";
            case 95, 96, 99 -> "#b45309";
            default -> "#334155";
        };
    }

    private String weatherIcon(int weatherCode) {
        return switch (weatherCode) {
            case 0 -> "☀";
            case 1, 2 -> "🌤";
            case 3 -> "☁";
            case 45, 48 -> "🌫";
            case 51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82 -> "🌧";
            case 71, 73, 75, 77, 85, 86 -> "❄";
            case 95, 96, 99 -> "⛈";
            default -> "•";
        };
    }

    private void fetchPlaceSuggestions() {
        String query = weatherPlaceBox.getEditor().getText();
        if (query == null || query.isBlank()) {
            setWeatherStatus("Enter a city or place name first.");
            return;
        }

        setWeatherStatus("Looking up places...");

        Task<List<WeatherLocation>> suggestionTask = new Task<>() {
            @Override
            protected List<WeatherLocation> call() {
                return geocodePlaceSuggestions(query.trim(), 5);
            }
        };

        suggestionTask.setOnSucceeded(e -> {
            List<WeatherLocation> suggestions = suggestionTask.getValue();
            weatherLocationLookup.clear();
            weatherPlaceBox.getItems().clear();

            for (WeatherLocation suggestion : suggestions) {
                weatherLocationLookup.put(suggestion.label, suggestion);
                weatherPlaceBox.getItems().add(suggestion.label);
            }

            if (suggestions.isEmpty()) {
                setWeatherStatus("No matching places found. You can still use latitude and longitude.");
                return;
            }

            weatherPlaceBox.getSelectionModel().select(0);
            applySelectedWeatherPlace();
            weatherPlaceBox.show();
            setWeatherStatus("Choose a place from the dropdown, then click Fetch Weather.");
        });

        suggestionTask.setOnFailed(e -> setWeatherStatus("Place lookup failed: " + suggestionTask.getException().getMessage()));

        Thread suggestionThread = new Thread(suggestionTask, "place-suggestions");
        suggestionThread.setDaemon(true);
        suggestionThread.start();
    }

    private void applySelectedWeatherPlace() {
        String selected = weatherPlaceBox.getSelectionModel().getSelectedItem();
        if (selected == null || selected.isBlank()) {
            selected = weatherPlaceBox.getEditor().getText();
        }

        WeatherLocation location = weatherLocationLookup.get(selected);
        if (location == null) {
            return;
        }

        weatherLatitudeField.setText(String.format("%.4f", location.latitude));
        weatherLongitudeField.setText(String.format("%.4f", location.longitude));
        weatherPlaceBox.getEditor().setText(location.label);
    }

    private WeatherLocation resolveWeatherLocation(String placeQuery, double fallbackLatitude, double fallbackLongitude) {
        if (placeQuery == null || placeQuery.isBlank()) {
            return new WeatherLocation(fallbackLatitude, fallbackLongitude, "Custom coordinates");
        }

        WeatherLocation cached = weatherLocationLookup.get(placeQuery.trim());
        if (cached != null) {
            return cached;
        }

        return geocodePlace(placeQuery.trim());
    }

    private WeatherLocation geocodePlace(String placeQuery) {
        List<WeatherLocation> results = geocodePlaceSuggestions(placeQuery, 1);
        if (results.isEmpty()) {
            throw new IllegalStateException("No matching place found for: " + placeQuery);
        }
        return results.get(0);
    }

    private List<WeatherLocation> geocodePlaceSuggestions(String placeQuery, int count) {
        try {
            String query = "name=" + URLEncoder.encode(placeQuery, StandardCharsets.UTF_8)
                + "&count=" + count + "&language=en&format=json";

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(GEOCODING_URL + "?" + query))
                .GET()
                .build();

            HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("Location lookup failed with status code: " + response.statusCode());
            }

            return parseGeocodingResults(response.body());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Location lookup interrupted.", ex);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to look up location.", ex);
        }
    }

    private List<WeatherLocation> parseGeocodingResults(String json) {
        List<WeatherLocation> suggestions = new ArrayList<>();
        int index = 0;

        while (true) {
            int nameIndex = json.indexOf("\"name\":\"", index);
            if (nameIndex < 0) {
                break;
            }

            int objectStart = json.lastIndexOf('{', nameIndex);
            int objectEnd = json.indexOf('}', nameIndex);
            if (objectStart < 0 || objectEnd < 0 || objectEnd <= objectStart) {
                break;
            }

            String objectJson = json.substring(objectStart, objectEnd + 1);
            String name = extractFirstString(objectJson, "name");
            String state = extractFirstString(objectJson, "admin1");
            String country = extractFirstString(objectJson, "country");
            double latitude = extractFirstDouble(objectJson, "latitude");
            double longitude = extractFirstDouble(objectJson, "longitude");
            String label = buildLocationLabel(name, state, country);
            suggestions.add(new WeatherLocation(latitude, longitude, label));

            index = objectEnd + 1;
        }

        return suggestions;
    }

    private String buildLocationLabel(String name, String state, String country) {
        String cleanedName = name == null ? "" : name.trim();
        String cleanedState = state == null ? "" : state.trim();
        String cleanedCountry = country == null ? "" : country.trim();

        StringBuilder label = new StringBuilder(cleanedName);
        if (!cleanedState.isBlank() && !cleanedState.equalsIgnoreCase(cleanedName)) {
            label.append(", ").append(cleanedState);
        }
        if (!cleanedCountry.isBlank() && !cleanedCountry.equalsIgnoreCase(cleanedState)) {
            label.append(", ").append(cleanedCountry);
        }

        if (label.isEmpty()) {
            return "Unknown location";
        }

        return label.toString();
    }

    private String extractFirstString(String json, String key) {
        Matcher matcher = Pattern.compile("\\\"" + Pattern.quote(key) + "\\\"\\s*:\\s*\\\"(.*?)\\\"").matcher(json);
        if (!matcher.find()) {
            return "";
        }
        return matcher.group(1);
    }

    private double extractFirstDouble(String json, String key) {
        Matcher matcher = Pattern.compile("\\\"" + Pattern.quote(key) + "\\\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)").matcher(json);
        if (!matcher.find()) {
            throw new IllegalStateException("Location not found: " + key + " is missing.");
        }
        return Double.parseDouble(matcher.group(1));
    }

    private List<WeatherService.ForecastEntry> forecastForDate(List<WeatherService.ForecastEntry> forecast, LocalDate date) {
        List<WeatherService.ForecastEntry> filtered = new ArrayList<>();

        for (WeatherService.ForecastEntry entry : forecast) {
            if (entry.getTime().toLocalDate().equals(date)) {
                filtered.add(entry);
            }
        }

        return filtered;
    }

    private String shortWeatherLabel(int weatherCode) {
        return switch (weatherCode) {
            case 0 -> "Clear";
            case 1, 2 -> "Mostly clear";
            case 3 -> "Cloudy";
            case 45, 48 -> "Fog";
            case 51, 53, 55 -> "Drizzle";
            case 56, 57 -> "Freezing drizzle";
            case 61, 63, 65 -> "Rain";
            case 66, 67 -> "Freezing rain";
            case 71, 73, 75 -> "Snow";
            case 77 -> "Snow grains";
            case 80, 81, 82 -> "Rain showers";
            case 85, 86 -> "Snow showers";
            case 95 -> "Thunderstorm";
            case 96, 99 -> "Thunderstorm + hail";
            default -> "Weather code " + weatherCode;
        };
    }

    private double parseDoubleOrDefault(String raw, double fallback) {
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private void createReminderIfRequested(Event event) {
        if (!reminderCheck.isSelected()) {
            return;
        }

        int minutesBefore = parseIntOrDefault(reminderMinutesField.getText(), 15);
        int snoozeMinutes = parseIntOrDefault(snoozeMinutesField.getText(), 10);

        if (minutesBefore < 0) {
            throw new IllegalArgumentException("Reminder minutes before cannot be negative.");
        }
        if (snoozeMinutes <= 0) {
            throw new IllegalArgumentException("Reminder snooze minutes must be greater than zero.");
        }

        Reminder reminder = new Reminder(nextReminderId(), event.getId(), event.getDescription(), minutesBefore, snoozeMinutes);
        reminder.recalculateTrigger(event);
        reminders.add(reminder);
        // Custom reminders are saved immediately so they survive app restart.
        CsvStorage.saveRemindersForUser(currentUser.getAccountID(), reminders);
    }

    private void createAutomaticStartTimeReminder(Event event) {
        // Auto reminder at the event start time.
        Reminder reminder = new Reminder(nextReminderId(), event.getId(), event.getDescription(), 0, 10);
        reminder.setTriggerAt(LocalDateTime.of(event.getDate(), event.getStartTime()));
        reminder.setFired(false);
        reminders.add(reminder);
    }

    private int nextReminderId() {
        int max = 0;
        for (Reminder reminder : reminders) {
            max = Math.max(max, reminder.getReminderId());
        }
        return max + 1;
    }

    private int parseIntOrDefault(String raw, int fallback) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private void refreshRemindersArea() {
        if (remindersListView == null && monthRemindersListView == null) {
            return;
        }

        // Build once, then project into both reminders surfaces (tab + month strip).
        List<String> rows = buildReminderRows();

        if (remindersListView != null) {
            remindersListView.getItems().clear();
            if (rows.isEmpty()) {
                remindersListView.getItems().add("No reminders configured for this session.");
            } else {
                remindersListView.getItems().addAll(rows);
            }
        }

        if (monthRemindersListView != null) {
            monthRemindersListView.getItems().clear();
            if (rows.isEmpty()) {
                monthRemindersListView.getItems().add("No upcoming reminders.");
            } else {
                int limit = Math.min(6, rows.size());
                monthRemindersListView.getItems().addAll(rows.subList(0, limit));
                if (rows.size() > limit) {
                    monthRemindersListView.getItems().add("... and " + (rows.size() - limit) + " more");
                }
            }
        }
    }

    private List<String> buildReminderRows() {
        List<String> rows = new ArrayList<>();
        if (reminders.isEmpty()) {
            return rows;
        }

        List<Reminder> sortedReminders = new ArrayList<>(reminders);
        sortedReminders.sort((left, right) -> {
            LocalDateTime leftTime = left.getTriggerAt();
            LocalDateTime rightTime = right.getTriggerAt();

            if (leftTime == null && rightTime == null) {
                return Integer.compare(left.getReminderId(), right.getReminderId());
            }
            if (leftTime == null) {
                return 1;
            }
            if (rightTime == null) {
                return -1;
            }

            int byTime = leftTime.compareTo(rightTime);
            if (byTime != 0) {
                return byTime;
            }

            return Integer.compare(left.getReminderId(), right.getReminderId());
        });

        for (Reminder reminder : sortedReminders) {
            Event event = findEventById(reminder.getEventId());
            StringBuilder row = new StringBuilder();
            String title = (event == null || event.getTitle() == null || event.getTitle().isBlank())
                ? "Event #" + reminder.getEventId()
                : event.getTitle();

            String trigger = reminder.getTriggerAt() == null
                ? "No time"
                : reminder.getTriggerAt().format(REMINDER_LIST_TIME_FORMAT);

            String status;
            if (!reminder.isActive()) {
                status = "Off";
            } else if (reminder.isFired()) {
                status = "Done";
            } else {
                status = "Active";
            }

            row.append("#")
                .append(reminder.getReminderId())
                .append(" | ")
                .append(title)
                .append(" | ")
                .append(trigger)
                .append(" | ")
                .append(status);

            rows.add(row.toString());
        }

        return rows;
    }

    private Event findEventById(int eventId) {
        return eventsById.get(eventId);
    }

    private void startReminderPolling() {
        // Poll the reminder list on a timer so due reminders can fire while the app is open.
        stopReminderPolling();

        reminderPoller = new Timeline(new KeyFrame(Duration.seconds(5), e -> checkDueReminders()));
        reminderPoller.setCycleCount(Timeline.INDEFINITE);
        reminderPoller.play();
        // Run one immediate pass so overdue reminders appear right after login/restore.
        checkDueReminders();
    }

    private void stopReminderPolling() {
        if (reminderPoller != null) {
            reminderPoller.stop();
            reminderPoller = null;
        }
    }

    private void checkDueReminders() {
        if (currentUser == null || reminders.isEmpty() || reminderNotificationOpen) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();

        for (Reminder reminder : reminders) {
            if (!reminder.isDue(now)) {
                continue;
            }

            // Mark as fired immediately so this reminder is not shown repeatedly while visible.
            reminder.setFired(true);
            if (currentUser != null) {
                CsvStorage.saveRemindersForUser(currentUser.getAccountID(), reminders);
            }

            // Show one due reminder at a time to avoid stacking notifications.
            Event event = findEventById(reminder.getEventId());
            showReminderNotification(reminder, event);
            refreshRemindersArea();
            break;
        }
    }

    private void showReminderNotification(Reminder reminder, Event event) {
        // Non-blocking toast-like reminder window shown near top-right of the app.
        String title = (event == null || event.getTitle() == null || event.getTitle().isBlank())
            ? "Untitled Event"
            : event.getTitle();

        Stage notificationStage = new Stage(StageStyle.DECORATED);
        if (stage != null) {
            notificationStage.initOwner(stage);
            notificationStage.initModality(Modality.NONE);
        }
        notificationStage.setTitle("Reminder");
        notificationStage.setAlwaysOnTop(true);

        Label headerLabel = new Label("EVENT DUE NOW: " + title);
        headerLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #991b1b;");

        Label messageLabel = new Label(buildReminderMessage(reminder, event));
        messageLabel.setWrapText(true);
        messageLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #334155;");

        Button dismissButton = new Button("Dismiss");
        dismissButton.setDefaultButton(true);

        HBox actions = new HBox(8, dismissButton);
        actions.setAlignment(Pos.CENTER_RIGHT);

        VBox root = new VBox(10, headerLabel, messageLabel, actions);
        root.setPadding(new Insets(12));
        root.setStyle("-fx-background-color: #fff7ed; -fx-border-color: #dc2626; -fx-border-width: 3;");

        Scene scene = new Scene(root, 440, 210);
        notificationStage.setScene(scene);

        dismissButton.setOnAction(e -> notificationStage.close());

        reminderNotificationOpen = true;
        notificationStage.setOnShown(e -> {
            notificationStage.toFront();
            notificationStage.requestFocus();
            if (stage != null) {
                // Anchor near the top-right corner of the app window.
                double x = stage.getX() + Math.max(0, stage.getWidth() - notificationStage.getWidth() - 24);
                double y = stage.getY() + 70;
                notificationStage.setX(x);
                notificationStage.setY(y);
            }
        });

        notificationStage.setOnHiding(e -> {
            reminderNotificationOpen = false;
            refreshRemindersArea();
            if (currentUser != null) {
                CsvStorage.saveRemindersForUser(currentUser.getAccountID(), reminders);
            }
        });

        notificationStage.show();
    }

    private String buildReminderMessage(Reminder reminder, Event event) {
        // Build a human-readable reminder summary for the notification window.
        StringBuilder sb = new StringBuilder();
        sb.append("Event ID: ").append(reminder.getEventId()).append("\n");
        sb.append("Minutes before: ").append(reminder.getMinutesBefore()).append("\n");
        sb.append("Action required: Dismiss this notification when acknowledged.\n");

        if (event != null) {
            sb.append("Date: ").append(event.getDate()).append("\n");
            DateTimeFormatter fmt = activeEventTimeFormatter();
            sb.append("Time: ").append(event.getStartTime().format(fmt)).append(" - ").append(event.getEndTime().format(fmt)).append("\n");
            sb.append("Description: ").append(event.getDescription() == null || event.getDescription().isBlank() ? "No description." : event.getDescription());
        }

        return sb.toString();
    }

    private List<Event> getDisplayedEvents() {
        if (currentUser == null) {
            return List.of();
        }

        if (selectedDate == null) {
            return new ArrayList<>(allEvents);
        }

        // Filter the side list down to the clicked date when a day is selected.
        List<Event> filtered = new ArrayList<>();
        for (Event event : allEvents) {
            if (selectedDate.equals(event.getDate())) {
                filtered.add(event);
            }
        }
        return filtered;
    }

    private void reloadEventsCache() {
        allEvents.clear();
        eventsById.clear();

        if (currentUser == null) {
            return;
        }

        List<Event> events = currentUser.loadMyEvents();
        allEvents.addAll(events);
        for (Event event : events) {
            eventsById.put(event.getId(), event);
        }
    }

    private void clearSelectedDate() {
        selectedDate = null;
        refreshEventList();
        rebuildMonthGrid();
    }

    private static class WeatherLocation {
        private final double latitude;
        private final double longitude;
        private final String label;

        private WeatherLocation(double latitude, double longitude, String label) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.label = label;
        }
    }

    private static final class QuickAddEventInput {
        // Captures quick-add dialog values without touching form controls directly.
        private final String title;
        private final String description;
        private final String startTime;
        private final String endTime;
        private final boolean recurring;
        private final boolean autoReminder;

        private QuickAddEventInput(String title, String description, String startTime, String endTime, boolean recurring, boolean autoReminder) {
            this.title = title;
            this.description = description;
            this.startTime = startTime;
            this.endTime = endTime;
            this.recurring = recurring;
            this.autoReminder = autoReminder;
        }
    }

    private static class WeatherForecastResult {
        private final WeatherLocation location;
        private final List<WeatherService.ForecastEntry> forecast;

        private WeatherForecastResult(WeatherLocation location, List<WeatherService.ForecastEntry> forecast) {
            this.location = location;
            this.forecast = forecast;
        }
    }
}
