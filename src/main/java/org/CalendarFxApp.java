package org;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Accordion;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

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
    private static final double DEFAULT_WEATHER_LATITUDE = 40.7128;
    private static final double DEFAULT_WEATHER_LONGITUDE = -74.0060;
    private static final String DEFAULT_WEATHER_PLACE = "New York";
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
    private ComboBox<String> recurrencePatternBox;
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
    private ListView<String> remindersListView;
    private Label weatherHeaderLabel;
    private VBox weatherCardsBox;
    private ScrollPane weatherScrollPane;
    private VBox goalsContainer;
    private Label userLabel;
    // Inline reminder strip shown directly under the month grid.
    private ListView<String> monthRemindersListView;
    private LocalDate selectedDate;
    private MonthViewController monthViewController;
    private ReminderNotifier reminderNotifier;
    private WeatherPaneController weatherPaneController;
    private EventUiController eventUiController;
    private final AuthSessionController authSessionController = new AuthSessionController();
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

    @Override
    public void stop() {
        if (reminderNotifier != null) {
            reminderNotifier.stop();
        }
        if (monthViewController != null) {
            monthViewController.stopLiveDateTimeClock();
        }
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
            if (eventUiController != null) {
                eventUiController.refreshEventList();
            }
            refreshRemindersArea();
            if (monthViewController != null) {
                monthViewController.rebuildMonthGrid();
                monthViewController.refreshLiveDateTimeMarker();
            }
        });
        sidebarToggle = new CheckBox("Show side panel");
        sidebarToggle.setSelected(settings.isSideBarOpen());
        sidebarToggle.setOnAction(e -> {
            settings.changeSideBarOpen(sidebarToggle.isSelected());
            updateResponsiveLayout();
        });
        recurrencePatternBox = new ComboBox<>();
        recurrencePatternBox.getItems().addAll(Event.RECURRENCE_NONE, Event.RECURRENCE_DAILY, Event.RECURRENCE_WEEKLY, Event.RECURRENCE_MONTHLY);
        recurrencePatternBox.setValue(Event.RECURRENCE_NONE);
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
            settings.changeWeatherUnit("Fahrenheit (°F)".equals(weatherUnitBox.getValue())
                ? Settings.WEATHER_UNIT_FAHRENHEIT
                : Settings.WEATHER_UNIT_CELSIUS);
            if (weatherPaneController != null) {
                weatherPaneController.rerenderCurrentWeatherForecast();
            }
        });
        weatherPlaceBox = new ComboBox<>();
        weatherPlaceBox.setEditable(true);
        weatherPlaceBox.setPromptText("e.g. New York");
        weatherPlaceBox.getEditor().setText(initialWeatherPlaceText());
        weatherPlaceBox.setOnAction(e -> {
            if (weatherPaneController != null) {
                weatherPaneController.applySelectedWeatherPlace();
            }
        });
        weatherLatitudeField = new TextField(formatCoordinate(initialWeatherLatitude()));
        weatherLongitudeField = new TextField(formatCoordinate(initialWeatherLongitude()));
        goalNameField = new TextField();
        goalCurrentField = new TextField("0");
        goalTargetField = new TextField("100");
        goalStatusLabel = new Label("");
        goalStatusLabel.getStyleClass().add("status-label");
        formStatusLabel = new Label("Ready");
        formStatusLabel.getStyleClass().add("status-label");
        selectedDate = null;
        userLabel = new Label("Not logged in");
        userLabel.getStyleClass().add("section-title");

        VBox form = new VBox(10);
        form.getStyleClass().add("form-pane");
        form.getChildren().add(userLabel);

        Button saveBtn = new Button("Save Event");
        saveBtn.setOnAction(e -> eventUiController.handleSaveEvent());

        Button refreshBtn = new Button("Refresh Events");
        refreshBtn.setOnAction(e -> eventUiController.refreshEventList());

        Button deleteBtn = new Button("Delete Selected Event");
        deleteBtn.setOnAction(e -> eventUiController.handleDeleteSelectedEvent());

        Button editBtn = new Button("Edit Selected Event");
        editBtn.setOnAction(e -> eventUiController.handleEditSelectedEvent());

        Button logoutBtn = new Button("Logout");
        logoutBtn.setOnAction(e -> handleLogout());

        Button fetchWeatherBtn = new Button("Fetch Weather");
        fetchWeatherBtn.setOnAction(e -> {
            if (weatherPaneController != null) {
                weatherPaneController.fetchWeatherForecast();
            }
        });

        Button findPlacesBtn = new Button("Find Places");
        findPlacesBtn.setOnAction(e -> {
            if (weatherPaneController != null) {
                weatherPaneController.fetchPlaceSuggestions();
            }
        });

        Button saveGoalBtn = new Button("Save Goal");
        saveGoalBtn.setOnAction(e -> handleSaveGoal());

        FlowPane eventActions = new FlowPane(8, 8, saveBtn, refreshBtn, editBtn, deleteBtn, logoutBtn);
        eventActions.getStyleClass().add("action-row");
        eventActions.setPrefWrapLength(320);

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
            new Label("Recurrence"), recurrencePatternBox,
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
        
        eventUiController = new EventUiController(
            titleField,
            descriptionField,
            datePicker,
            startTimeBox,
            endTimeBox,
            recurrencePatternBox,
            autoReminderCheck,
            reminderCheck,
            reminderMinutesField,
            snoozeMinutesField,
            formStatusLabel,
            authStatusLabel,
            userLabel,
            eventListTitle,
            eventsListView,
            () -> currentUser,
            () -> reminders,
            this::getDisplayedEvents,
            () -> selectedDate,
            () -> stage,
            () -> monthViewController,
            this::activeEventTimeFormatter,
            (unused) -> refreshRemindersArea()  // onRefreshRemindersArea
            , (unused) -> reloadEventsCache()  // onReloadEventsCache
            , date -> { selectedDate = date; }  // onUpdateSelectedDate
            , () -> buildTimePicker("9:00 AM")  // timePickerFactory
        );
        
        // Double-clicking an event still opens its read-only details dialog.
        eventsListView.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                int index = eventsListView.getSelectionModel().getSelectedIndex();
                if (index >= 0) {
                    if (index < eventUiController.displayedEvents.size()) {
                        eventUiController.showEventDetails(eventUiController.displayedEvents.get(index));
                    }
                }
            }
        });
        
        monthViewController = new MonthViewController(
            settings,
            () -> currentUser,
            () -> allEvents,
            () -> selectedDate,
            eventUiController::selectDateForEventCreation,
            eventUiController::showQuickAddEventDialog,
            eventUiController::showEventsForDate,
            eventUiController::showEventDetails,
            this::clearSelectedDate
        );
        calendarPanel = monthViewController.buildCalendarPanel();
        monthRemindersListView = monthViewController.getMonthRemindersListView();
        refreshRemindersArea();

        if (reminderNotifier != null) {
            reminderNotifier.stop();
        }
        reminderNotifier = new ReminderNotifier(
            () -> stage,
            () -> currentUser,
            () -> reminders,
            this::findEventById,
            this::refreshRemindersArea,
            this::activeEventTimeFormatter
        );
        reminderNotifier.start();

        weatherPaneController = new WeatherPaneController(
            settings,
            weatherUnitBox,
            weatherPlaceBox,
            weatherLatitudeField,
            weatherLongitudeField,
            weatherHeaderLabel,
            weatherCardsBox
        );

        if (settings.hasSavedWeatherLocation()) {
            // Pre-populate the location lookup so fetch doesn't need to re-geocode
            weatherPaneController.populateSavedWeatherLocation(
                settings.getWeatherLocationLabel(),
                settings.getWeatherLatitude(),
                settings.getWeatherLongitude()
            );
            weatherPaneController.setWeatherStatus("Loading saved weather location...");
            weatherPaneController.fetchWeatherForecast();
        } else {
            weatherPaneController.setWeatherStatus("Click Fetch Weather to load the forecast.");
        }

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

        MenuItem editEventMenuItem = new MenuItem("Edit Selected Event");
        editEventMenuItem.setOnAction(e -> {
            if (eventUiController != null) {
                eventUiController.handleEditSelectedEvent();
            }
        });
        MenuItem deleteEventMenuItem = new MenuItem("Delete Selected Event");
        deleteEventMenuItem.setOnAction(e -> {
            if (eventUiController != null) {
                eventUiController.handleDeleteSelectedEvent();
            }
        });
        eventsListView.setContextMenu(new ContextMenu(editEventMenuItem, deleteEventMenuItem));

        Button editEventBtn = new Button("Edit Selected Event");
        editEventBtn.setMaxWidth(Double.MAX_VALUE);
        editEventBtn.setOnAction(e -> {
            if (eventUiController != null) {
                eventUiController.handleEditSelectedEvent();
            }
        });

        Button deleteEventBtn = new Button("Delete Selected Event");
        deleteEventBtn.setMaxWidth(Double.MAX_VALUE);
        deleteEventBtn.setOnAction(e -> {
            if (eventUiController != null) {
                eventUiController.handleDeleteSelectedEvent();
            }
        });

        // Events tab mirrors reminders tab with edit/delete buttons below the list.
        VBox eventsPane = new VBox(8, eventListTitle, eventsListView, editEventBtn, deleteEventBtn);
        VBox.setVgrow(eventsListView, Priority.ALWAYS);
        eventsPane.setPadding(new Insets(8));

        Label reminderTitle = new Label("Reminders");
        reminderTitle.getStyleClass().add("section-title");
        remindersListView = new ListView<>();
        remindersListView.setPlaceholder(new Label("No reminders configured."));
        remindersListView.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                handleEditSelectedReminder();
            }
        });

        MenuItem editReminderMenuItem = new MenuItem("Edit Selected Reminder");
        editReminderMenuItem.setOnAction(e -> handleEditSelectedReminder());
        MenuItem deleteReminderMenuItem = new MenuItem("Delete Selected Reminder");
        deleteReminderMenuItem.setOnAction(e -> handleDeleteSelectedReminder());
        remindersListView.setContextMenu(new ContextMenu(editReminderMenuItem, deleteReminderMenuItem));

        Button editReminderBtn = new Button("Edit Selected Reminder");
        editReminderBtn.setMaxWidth(Double.MAX_VALUE);
        editReminderBtn.setOnAction(e -> handleEditSelectedReminder());

        Button deleteReminderBtn = new Button("Delete Selected Reminder");
        deleteReminderBtn.setMaxWidth(Double.MAX_VALUE);
        deleteReminderBtn.setOnAction(e -> handleDeleteSelectedReminder());
        VBox remindersPane = new VBox(8, reminderTitle, remindersListView, editReminderBtn, deleteReminderBtn);
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


    private void handleCreateAccount() {
        String username = usernameField.getText();
        String password = passwordField.getText();

        try {
            authStatusLabel.setText(authSessionController.createAccount(username, password));
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
            UserAccount authenticatedUser = authSessionController.login(username, password);
            openUserSession(authenticatedUser, rememberMe);
            if (!rememberMe) {
                authSessionController.clearCurrentSession();
            }
        } catch (IllegalArgumentException ex) {
            authStatusLabel.setText(ex.getMessage());
        }
    }

    private boolean tryRestorePersistedSession() {
        // Uses persisted account ID from session.csv to skip manual login.
        UserAccount restoredUser = authSessionController.tryRestorePersistedSession();
        if (restoredUser == null) {
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
        synchronizeReminderTriggers();
        goals.clear();
        goals.addAll(CsvStorage.loadGoalsForUser(currentUser.getAccountID()));

        userLabel.setText("Logged in as: " + currentUser.getUsername() + " (ID " + currentUser.getAccountID() + ")");
        if (eventUiController != null) {
            eventUiController.refreshEventList();
        }
        refreshGoalsArea();

        if (persistSession) {
            authSessionController.saveCurrentSession(currentUser);
        }
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

        Button editGoalBtn = new Button("Edit");
        editGoalBtn.setOnAction(e -> handleEditGoal(goal));
        Button deleteGoalBtn = new Button("Delete");
        deleteGoalBtn.setOnAction(e -> handleDeleteGoal(goal));
        HBox actions = new HBox(8, editGoalBtn, deleteGoalBtn);

        VBox card = new VBox(6, name, bar, details, actions);
        card.getStyleClass().add("weather-card");
        card.setPadding(new Insets(10));
        return card;
    }

    private void handleEditGoal(Goal goal) {
        if (currentUser == null) {
            authStatusLabel.setText("No active user session.");
            return;
        }

        Dialog<GoalEditInput> dialog = new Dialog<>();
        dialog.initOwner(stage);
        dialog.setTitle("Edit Goal");
        dialog.setHeaderText("Edit goal #" + goal.getGoalId());

        ButtonType saveType = new ButtonType("Save Changes", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);

        TextField nameField = new TextField(goal.getName());
        TextField currentField = new TextField(String.valueOf(goal.getCurrentValue()));
        TextField targetField = new TextField(String.valueOf(goal.getTargetValue()));

        GridPane content = new GridPane();
        content.setHgap(8);
        content.setVgap(8);
        content.add(new Label("Goal name"), 0, 0);
        content.add(nameField, 1, 0);
        content.add(new Label("Current value"), 0, 1);
        content.add(currentField, 1, 1);
        content.add(new Label("Target value"), 0, 2);
        content.add(targetField, 1, 2);
        dialog.getDialogPane().setContent(content);

        dialog.setResultConverter(buttonType -> {
            if (buttonType != saveType) {
                return null;
            }
            return new GoalEditInput(nameField.getText(), currentField.getText(), targetField.getText());
        });

        Optional<GoalEditInput> result = dialog.showAndWait();
        if (result.isEmpty()) {
            return;
        }

        try {
            GoalEditInput input = result.get();
            String normalizedName = input.name == null ? "" : input.name.trim();
            if (normalizedName.isBlank()) {
                throw new IllegalArgumentException("Goal name is required.");
            }

            double currentValue = Double.parseDouble(input.currentValue.trim());
            double targetValue = Double.parseDouble(input.targetValue.trim());
            if (targetValue <= 0) {
                throw new IllegalArgumentException("Target value must be greater than 0.");
            }
            if (currentValue < 0) {
                throw new IllegalArgumentException("Current value cannot be negative.");
            }

            for (Goal existing : goals) {
                if (existing.getGoalId() != goal.getGoalId() && existing.getName().equalsIgnoreCase(normalizedName)) {
                    throw new IllegalArgumentException("A different goal already uses this name.");
                }
            }

            goal.setName(normalizedName);
            goal.setCurrentValue(currentValue);
            goal.setTargetValue(targetValue);
            CsvStorage.saveGoalsForUser(currentUser.getAccountID(), goals);
            refreshGoalsArea();
            goalStatusLabel.setText("Updated goal #" + goal.getGoalId());
        } catch (NumberFormatException ex) {
            goalStatusLabel.setText("Current and target values must be numbers.");
        } catch (IllegalArgumentException ex) {
            goalStatusLabel.setText(ex.getMessage());
        }
    }

    private void handleDeleteGoal(Goal goal) {
        if (currentUser == null) {
            authStatusLabel.setText("No active user session.");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Delete Goal");
        confirm.setHeaderText("Delete goal \"" + goal.getName() + "\"?");
        confirm.setContentText("This goal will be permanently removed.");

        Optional<ButtonType> decision = confirm.showAndWait();
        if (decision.isEmpty() || decision.get() != ButtonType.OK) {
            return;
        }

        goals.removeIf(existing -> existing.getGoalId() == goal.getGoalId());
        CsvStorage.saveGoalsForUser(currentUser.getAccountID(), goals);
        refreshGoalsArea();
        goalStatusLabel.setText("Deleted goal #" + goal.getGoalId());
    }

    private void handleLogout() {
        if (currentUser != null) {
            CsvStorage.saveRemindersForUser(currentUser.getAccountID(), reminders);
            CsvStorage.saveGoalsForUser(currentUser.getAccountID(), goals);
        }
        // Logout always clears persisted session regardless of Remember me default.
        authSessionController.clearCurrentSession();
        currentUser = null;
        selectedDate = null;
        reminders.clear();
        goals.clear();
        allEvents.clear();
        eventsById.clear();
        if (weatherPaneController != null) {
            weatherPaneController.clearCurrentWeatherState();
        }
        if (reminderNotifier != null) {
            reminderNotifier.stop();
            reminderNotifier.resetState();
        }
        if (monthViewController != null) {
            monthViewController.stopLiveDateTimeClock();
        }
        stage.setScene(buildAuthScene());
        authStatusLabel.setText("Logged out.");
    }

    private String initialWeatherPlaceText() {
        if (settings.hasSavedWeatherLocation()) {
            return settings.getWeatherLocationLabel();
        }
        return DEFAULT_WEATHER_PLACE;
    }

    private double initialWeatherLatitude() {
        if (settings.hasSavedWeatherLocation()) {
            return settings.getWeatherLatitude();
        }
        return DEFAULT_WEATHER_LATITUDE;
    }

    private double initialWeatherLongitude() {
        if (settings.hasSavedWeatherLocation()) {
            return settings.getWeatherLongitude();
        }
        return DEFAULT_WEATHER_LONGITUDE;
    }

    private String formatCoordinate(double value) {
        return String.format(Locale.US, "%.4f", value);
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

    private void synchronizeReminderTriggers() {
        if (currentUser == null || reminders.isEmpty()) {
            return;
        }

        boolean changed = false;
        for (Reminder reminder : reminders) {
            Event event = findEventById(reminder.getEventId());
            if (event == null) {
                continue;
            }

            LocalDateTime before = reminder.getTriggerAt();
            boolean beforeFired = reminder.isFired();
            reminder.recalculateTrigger(event);
            if ((before == null && reminder.getTriggerAt() != null)
                || (before != null && !before.equals(reminder.getTriggerAt()))
                || beforeFired != reminder.isFired()) {
                changed = true;
            }
        }

        if (changed) {
            CsvStorage.saveRemindersForUser(currentUser.getAccountID(), reminders);
        }
    }

    private List<String> buildReminderRows() {
        List<String> rows = new ArrayList<>();
        if (reminders.isEmpty()) {
            return rows;
        }

        for (Reminder reminder : remindersSortedForDisplay()) {
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

    private List<Reminder> remindersSortedForDisplay() {
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

        return sortedReminders;
    }

    private void handleDeleteSelectedReminder() {
        if (currentUser == null) {
            if (authStatusLabel != null) {
                authStatusLabel.setText("No active user session.");
            }
            return;
        }

        if (remindersListView == null) {
            return;
        }

        int selectedIndex = remindersListView.getSelectionModel().getSelectedIndex();
        List<Reminder> sortedReminders = remindersSortedForDisplay();
        if (selectedIndex < 0 || selectedIndex >= sortedReminders.size()) {
            if (formStatusLabel != null) {
                formStatusLabel.setText("Select a reminder from the Reminders list first.");
            }
            return;
        }

        Reminder selectedReminder = sortedReminders.get(selectedIndex);
        Event event = findEventById(selectedReminder.getEventId());
        String eventTitle = (event == null || event.getTitle() == null || event.getTitle().isBlank())
            ? "Event #" + selectedReminder.getEventId()
            : event.getTitle();

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Delete Reminder");
        confirm.setHeaderText("Delete reminder #" + selectedReminder.getReminderId() + "?");
        confirm.setContentText("This reminder for \"" + eventTitle + "\" will be permanently removed.");

        Optional<ButtonType> decision = confirm.showAndWait();
        if (decision.isEmpty() || decision.get() != ButtonType.OK) {
            return;
        }

        reminders.removeIf(reminder -> reminder.getReminderId() == selectedReminder.getReminderId());
        CsvStorage.saveRemindersForUser(currentUser.getAccountID(), reminders);
        refreshRemindersArea();

        if (formStatusLabel != null) {
            formStatusLabel.setText("Deleted reminder #" + selectedReminder.getReminderId());
        }
    }

    private void handleEditSelectedReminder() {
        if (currentUser == null) {
            if (authStatusLabel != null) {
                authStatusLabel.setText("No active user session.");
            }
            return;
        }

        if (remindersListView == null) {
            return;
        }

        int selectedIndex = remindersListView.getSelectionModel().getSelectedIndex();
        List<Reminder> sortedReminders = remindersSortedForDisplay();
        if (selectedIndex < 0 || selectedIndex >= sortedReminders.size()) {
            if (formStatusLabel != null) {
                formStatusLabel.setText("Select a reminder from the Reminders list first.");
            }
            return;
        }

        Reminder selectedReminder = sortedReminders.get(selectedIndex);

        Dialog<ReminderEditInput> dialog = new Dialog<>();
        dialog.initOwner(stage);
        dialog.setTitle("Edit Reminder");
        dialog.setHeaderText("Edit reminder #" + selectedReminder.getReminderId());

        ButtonType saveType = new ButtonType("Save Changes", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);

        TextField minutesBeforeField = new TextField(String.valueOf(selectedReminder.getMinutesBefore()));
        TextField snoozeField = new TextField(String.valueOf(selectedReminder.getSnoozeLength()));
        CheckBox activeCheck = new CheckBox("Reminder enabled");
        activeCheck.setSelected(selectedReminder.isActive());

        GridPane content = new GridPane();
        content.setHgap(8);
        content.setVgap(8);
        content.add(new Label("Minutes before event"), 0, 0);
        content.add(minutesBeforeField, 1, 0);
        content.add(new Label("Snooze minutes"), 0, 1);
        content.add(snoozeField, 1, 1);
        content.add(activeCheck, 1, 2);
        dialog.getDialogPane().setContent(content);

        dialog.setResultConverter(buttonType -> {
            if (buttonType != saveType) {
                return null;
            }
            return new ReminderEditInput(minutesBeforeField.getText(), snoozeField.getText(), activeCheck.isSelected());
        });

        Optional<ReminderEditInput> result = dialog.showAndWait();
        if (result.isEmpty()) {
            return;
        }

        try {
            ReminderEditInput input = result.get();
            int minutesBefore = Integer.parseInt(input.minutesBefore.trim());
            int snoozeMinutes = Integer.parseInt(input.snoozeLength.trim());

            if (minutesBefore < 0) {
                throw new IllegalArgumentException("Reminder minutes before cannot be negative.");
            }
            if (snoozeMinutes <= 0) {
                throw new IllegalArgumentException("Reminder snooze minutes must be greater than zero.");
            }

            selectedReminder.setMinutesBefore(minutesBefore);
            selectedReminder.setSnoozeLength(snoozeMinutes);
            selectedReminder.setActive(input.active);
            selectedReminder.setFired(false);

            Event event = findEventById(selectedReminder.getEventId());
            if (event != null) {
                selectedReminder.recalculateTrigger(event);
                selectedReminder.setEventDescription(event.getDescription());
            }

            CsvStorage.saveRemindersForUser(currentUser.getAccountID(), reminders);
            refreshRemindersArea();
            if (formStatusLabel != null) {
                formStatusLabel.setText("Updated reminder #" + selectedReminder.getReminderId());
            }
        } catch (NumberFormatException ex) {
            if (formStatusLabel != null) {
                formStatusLabel.setText("Reminder values must be valid numbers.");
            }
        } catch (IllegalArgumentException ex) {
            if (formStatusLabel != null) {
                formStatusLabel.setText(ex.getMessage());
            }
        }
    }

    private Event findEventById(int eventId) {
        return eventsById.get(eventId);
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
            if (EventService.occursOn(event, selectedDate)) {
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
        if (eventUiController != null) {
            eventUiController.refreshEventList();
        }
        if (monthViewController != null) {
            monthViewController.rebuildMonthGrid();
        }
    }

    private static final class ReminderEditInput {
        private final String minutesBefore;
        private final String snoozeLength;
        private final boolean active;

        private ReminderEditInput(String minutesBefore, String snoozeLength, boolean active) {
            this.minutesBefore = minutesBefore;
            this.snoozeLength = snoozeLength;
            this.active = active;
        }
    }

    private static final class GoalEditInput {
        private final String name;
        private final String currentValue;
        private final String targetValue;

        private GoalEditInput(String name, String currentValue, String targetValue) {
            this.name = name;
            this.currentValue = currentValue;
            this.targetValue = targetValue;
        }
    }

}
