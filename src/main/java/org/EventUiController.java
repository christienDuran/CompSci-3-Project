package org;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
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
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

/**
 * Encapsulates event creation, editing, deletion, and display UI logic.
 *
 * Dependencies are injected via suppliers for read-only state and consumers for actions.
 */
public class EventUiController {
    // UI controls passed in
    private final TextField titleField;
    private final TextField descriptionField;
    private final DatePicker datePicker;
    private final ComboBox<String> startTimeBox;
    private final ComboBox<String> endTimeBox;
    private final CheckBox recurringCheck;
    private final CheckBox autoReminderCheck;
    private final CheckBox reminderCheck;
    private final TextField reminderMinutesField;
    private final TextField snoozeMinutesField;
    private final Label formStatusLabel;
    private final Label authStatusLabel;
    private final Label userLabel;
    private final Label eventListTitle;
    private final ListView<String> eventsListView;

    // State suppliers
    private final Supplier<UserAccount> currentUserSupplier;
    private final Supplier<List<Reminder>> remindersSupplier;
    private final Supplier<List<Event>> displayedEventsSupplier;
    private final Supplier<LocalDate> selectedDateSupplier;
    private final Supplier<Stage> stageSupplier;
    private final Supplier<MonthViewController> monthViewControllerSupplier;
    private final Supplier<DateTimeFormatter> eventTimeFormatterSupplier;

    // State that needs to be mutable (displayedEvents, selectedDate)
    public List<Event> displayedEvents = new ArrayList<>();

    // Callback consumers for external actions
    private final Consumer<Void> onRefreshRemindersArea;
    private final Consumer<String> onReloadEventsCache;
    private final Consumer<LocalDate> onUpdateSelectedDate;

    // Helper lambda for building time picker (captured from CalendarFxApp)
    private final Supplier<ComboBox<String>> timePickerFactory;

    public EventUiController(
        TextField titleField,
        TextField descriptionField,
        DatePicker datePicker,
        ComboBox<String> startTimeBox,
        ComboBox<String> endTimeBox,
        CheckBox recurringCheck,
        CheckBox autoReminderCheck,
        CheckBox reminderCheck,
        TextField reminderMinutesField,
        TextField snoozeMinutesField,
        Label formStatusLabel,
        Label authStatusLabel,
        Label userLabel,
        Label eventListTitle,
        ListView<String> eventsListView,
        Supplier<UserAccount> currentUserSupplier,
        Supplier<List<Reminder>> remindersSupplier,
        Supplier<List<Event>> displayedEventsSupplier,
        Supplier<LocalDate> selectedDateSupplier,
        Supplier<Stage> stageSupplier,
        Supplier<MonthViewController> monthViewControllerSupplier,
        Supplier<DateTimeFormatter> eventTimeFormatterSupplier,
        Consumer<Void> onRefreshRemindersArea,
        Consumer<String> onReloadEventsCache,
        Consumer<LocalDate> onUpdateSelectedDate,
        Supplier<ComboBox<String>> timePickerFactory
    ) {
        this.titleField = titleField;
        this.descriptionField = descriptionField;
        this.datePicker = datePicker;
        this.startTimeBox = startTimeBox;
        this.endTimeBox = endTimeBox;
        this.recurringCheck = recurringCheck;
        this.autoReminderCheck = autoReminderCheck;
        this.reminderCheck = reminderCheck;
        this.reminderMinutesField = reminderMinutesField;
        this.snoozeMinutesField = snoozeMinutesField;
        this.formStatusLabel = formStatusLabel;
        this.authStatusLabel = authStatusLabel;
        this.userLabel = userLabel;
        this.eventListTitle = eventListTitle;
        this.eventsListView = eventsListView;
        this.currentUserSupplier = currentUserSupplier;
        this.remindersSupplier = remindersSupplier;
        this.displayedEventsSupplier = displayedEventsSupplier;
        this.selectedDateSupplier = selectedDateSupplier;
        this.stageSupplier = stageSupplier;
        this.monthViewControllerSupplier = monthViewControllerSupplier;
        this.eventTimeFormatterSupplier = eventTimeFormatterSupplier;
        this.onRefreshRemindersArea = onRefreshRemindersArea;
        this.onReloadEventsCache = onReloadEventsCache;
        this.onUpdateSelectedDate = onUpdateSelectedDate;
        this.timePickerFactory = timePickerFactory;
    }

    public void handleSaveEvent() {
        UserAccount currentUser = currentUserSupplier.get();
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
            CsvStorage.saveRemindersForUser(currentUser.getAccountID(), remindersSupplier.get());
            titleField.clear();
            descriptionField.clear();
            formStatusLabel.setText("Saved event #" + saved.getId());
            refreshEventList();
            MonthViewController monthViewController = monthViewControllerSupplier.get();
            if (monthViewController != null) {
                monthViewController.rebuildMonthGrid();
            }
            refreshRemindersArea();
            userLabel.setText("Logged in as: " + currentUser.getUsername() + " (ID " + currentUser.getAccountID() + ") - Saved event #" + saved.getId());
        } catch (IllegalArgumentException ex) {
            formStatusLabel.setText(ex.getMessage());
        }
    }

    public void handleDeleteSelectedEvent() {
        UserAccount currentUser = currentUserSupplier.get();
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
        remindersSupplier.get().removeIf(reminder -> reminder.getEventId() == selectedEvent.getId());
        CsvStorage.saveRemindersForUser(currentUser.getAccountID(), remindersSupplier.get());

        formStatusLabel.setText("Deleted event #" + selectedEvent.getId());
        refreshEventList();
    }

    public void handleEditSelectedEvent() {
        // Edit action uses the current Events-tab list selection.
        int selectedIndex = eventsListView == null ? -1 : eventsListView.getSelectionModel().getSelectedIndex();
        handleEditEventAtIndex(selectedIndex);
    }

    public void handleEditEventAtIndex(int selectedIndex) {
        UserAccount currentUser = currentUserSupplier.get();
        if (currentUser == null) {
            authStatusLabel.setText("No active user session.");
            return;
        }

        if (selectedIndex < 0 || selectedIndex >= displayedEvents.size()) {
            formStatusLabel.setText("Select an event from the Events list first.");
            return;
        }

        // Index-based edit keeps all event-edit entry points on the same logic path.
        Event selectedEvent = displayedEvents.get(selectedIndex);
        Dialog<EventEditInput> dialog = new Dialog<>();
        dialog.initOwner(stageSupplier.get());
        dialog.setTitle("Edit Event");
        dialog.setHeaderText("Edit event #" + selectedEvent.getId());

        ButtonType saveType = new ButtonType("Save Changes", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);

        TextField editTitleField = new TextField(selectedEvent.getTitle());
        TextField editDescriptionField = new TextField(selectedEvent.getDescription());
        DatePicker editDatePicker = new DatePicker(selectedEvent.getDate());
        ComboBox<String> editStartBox = timePickerFactory.get();
        ComboBox<String> editEndBox = timePickerFactory.get();
        editStartBox.setValue(selectedEvent.getStartTime().format(DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)));
        editEndBox.setValue(selectedEvent.getEndTime().format(DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)));
        bindEndTimeAfterStart(editStartBox, editEndBox);
        CheckBox editRecurringCheck = new CheckBox("Recurring");
        editRecurringCheck.setSelected(selectedEvent.isRecurring());

        GridPane content = new GridPane();
        content.setHgap(8);
        content.setVgap(8);
        content.add(new Label("Title"), 0, 0);
        content.add(editTitleField, 1, 0);
        content.add(new Label("Description"), 0, 1);
        content.add(editDescriptionField, 1, 1);
        content.add(new Label("Date"), 0, 2);
        content.add(editDatePicker, 1, 2);
        content.add(new Label("Start"), 0, 3);
        content.add(editStartBox, 1, 3);
        content.add(new Label("End"), 0, 4);
        content.add(editEndBox, 1, 4);
        content.add(editRecurringCheck, 1, 5);
        dialog.getDialogPane().setContent(content);

        dialog.setResultConverter(buttonType -> {
            if (buttonType != saveType) {
                return null;
            }
            return new EventEditInput(
                editTitleField.getText(),
                editDescriptionField.getText(),
                editDatePicker.getValue(),
                selectedTimeValue(editStartBox),
                selectedTimeValue(editEndBox),
                editRecurringCheck.isSelected()
            );
        });

        Optional<EventEditInput> result = dialog.showAndWait();
        if (result.isEmpty()) {
            return;
        }

        try {
            EventEditInput input = result.get();
            String normalizedTitle = input.title == null ? "" : input.title.trim();
            if (normalizedTitle.isBlank()) {
                throw new IllegalArgumentException("Event title is required.");
            }
            if (input.date == null) {
                throw new IllegalArgumentException("Event date is required.");
            }

            LocalTime start = parseFlexibleTime(input.startTime);
            if (start == null) {
                throw new IllegalArgumentException("Start time must be like 9:00 AM.");
            }
            LocalTime end = parseFlexibleTime(input.endTime);
            if (end == null) {
                throw new IllegalArgumentException("End time must be like 10:30 AM.");
            }
            if (end.isBefore(start)) {
                throw new IllegalArgumentException("End time cannot be before start time.");
            }

            selectedEvent.editEvent(
                normalizedTitle,
                input.description == null ? "" : input.description.trim(),
                input.date,
                start,
                end,
                input.recurring
            );
            CsvStorage.updateEventForUser(currentUser.getAccountID(), selectedEvent);

            // Keep reminder trigger/description aligned when event timing or description changes.
            List<Reminder> reminders = remindersSupplier.get();
            for (Reminder reminder : reminders) {
                if (reminder.getEventId() != selectedEvent.getId()) {
                    continue;
                }
                reminder.setEventDescription(selectedEvent.getDescription());
                if (reminder.getMinutesBefore() == 0) {
                    reminder.setTriggerAt(LocalDateTime.of(selectedEvent.getDate(), selectedEvent.getStartTime()));
                    reminder.setFired(false);
                } else {
                    reminder.recalculateTrigger(selectedEvent);
                }
            }
            CsvStorage.saveRemindersForUser(currentUser.getAccountID(), reminders);

            titleField.setText(selectedEvent.getTitle());
            descriptionField.setText(selectedEvent.getDescription());
            datePicker.setValue(selectedEvent.getDate());
            startTimeBox.setValue(selectedEvent.getStartTime().format(DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)));
            endTimeBox.setValue(selectedEvent.getEndTime().format(DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)));
            recurringCheck.setSelected(selectedEvent.isRecurring());

            formStatusLabel.setText("Updated event #" + selectedEvent.getId());
            refreshEventList();
        } catch (IllegalArgumentException ex) {
            formStatusLabel.setText(ex.getMessage());
        }
    }

    public void selectDateForEventCreation(LocalDate date) {
        onUpdateSelectedDate.accept(date);
        
        if (datePicker != null) {
            datePicker.setValue(date);
        }

        if (formStatusLabel != null) {
            formStatusLabel.setText("Selected " + date + ". Enter title/details, then click Save Event.");
        }

        refreshEventList();
    }

    public void showQuickAddEventDialog(LocalDate date) {
        UserAccount currentUser = currentUserSupplier.get();
        if (currentUser == null) {
            authStatusLabel.setText("Please log in first.");
            return;
        }

        // Lightweight event dialog launched from a single click on a day cell.
        Dialog<QuickAddEventInput> dialog = new Dialog<>();
        dialog.initOwner(stageSupplier.get());
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
        ComboBox<String> quickStartBox = timePickerFactory.get();
        quickStartBox.setValue(defaultStart.isBlank() ? "9:00 AM" : defaultStart);
        ComboBox<String> quickEndBox = timePickerFactory.get();
        quickEndBox.setValue(defaultEnd.isBlank() ? "10:00 AM" : defaultEnd);
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
            CsvStorage.saveRemindersForUser(currentUser.getAccountID(), remindersSupplier.get());

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

    public void showEventDetails(Event event) {
        // This popup is the detailed event view opened from a title button or the day list.
        DateTimeFormatter fmt = eventTimeFormatterSupplier.get();
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

    public void showEventsForDate(LocalDate date, List<Event> events) {
        // This popup lists all events on a selected date and keeps each entry clickable.
        Stage popup = new Stage();
        popup.initOwner(stageSupplier.get());
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

    public String formatEventSummary(Event event) {
        DateTimeFormatter fmt = eventTimeFormatterSupplier.get();
        return "#" + event.getId() + " | "
            + event.getTitle() + " | "
            + event.getStartTime().format(fmt) + " - "
            + event.getEndTime().format(fmt)
            + (event.isRecurring() ? " | recurring" : "");
    }

    public void refreshEventList() {
        if (eventsListView == null || eventListTitle == null) {
            return;
        }

        UserAccount currentUser = currentUserSupplier.get();
        if (currentUser == null) {
            eventListTitle.setText("Events");
            eventsListView.getItems().clear();
            displayedEvents = new ArrayList<>();
            return;
        }

        onReloadEventsCache.accept("");
        List<Event> events = displayedEventsSupplier.get();
        displayedEvents = new ArrayList<>(events);

        LocalDate selectedDate = selectedDateSupplier.get();
        eventListTitle.setText(selectedDate == null
            ? "Events for " + currentUser.getUsername()
            : "Events for " + currentUser.getUsername() + " on " + selectedDate);

        eventsListView.getItems().clear();
        if (events.isEmpty()) {
            eventsListView.getItems().add(selectedDate == null ? "No events yet." : "No events on this date.");
        } else {
            DateTimeFormatter fmt = eventTimeFormatterSupplier.get();
            for (Event event : events) {
                eventsListView.getItems().add("#" + event.getId()
                    + "  " + event.getDate()
                    + "  " + event.getStartTime().format(fmt)
                    + " - " + event.getEndTime().format(fmt)
                    + "  " + event.getTitle()
                    + (event.isRecurring() ? " (recurring)" : ""));
            }
        }

        MonthViewController monthViewController = monthViewControllerSupplier.get();
        if (monthViewController != null) {
            monthViewController.rebuildMonthGrid();
        }
        refreshRemindersArea();
    }

    public void refreshRemindersArea() {
        onRefreshRemindersArea.accept(null);
    }

    public void createReminderIfRequested(Event event) {
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

        UserAccount currentUser = currentUserSupplier.get();
        List<Reminder> reminders = remindersSupplier.get();
        Reminder reminder = new Reminder(nextReminderId(reminders), event.getId(), event.getDescription(), minutesBefore, snoozeMinutes);
        reminder.recalculateTrigger(event);
        reminders.add(reminder);
        // Custom reminders are saved immediately so they survive app restart.
        CsvStorage.saveRemindersForUser(currentUser.getAccountID(), reminders);
    }

    public void createAutomaticStartTimeReminder(Event event) {
        // Auto reminder at the event start time.
        List<Reminder> reminders = remindersSupplier.get();
        Reminder reminder = new Reminder(nextReminderId(reminders), event.getId(), event.getDescription(), 0, 10);
        reminder.setTriggerAt(LocalDateTime.of(event.getDate(), event.getStartTime()));
        reminder.setFired(false);
        reminders.add(reminder);
    }

    private int nextReminderId(List<Reminder> reminders) {
        int max = 0;
        for (Reminder reminder : reminders) {
            max = Math.max(max, reminder.getReminderId());
        }
        return max + 1;
    }

    private LocalTime parseFlexibleTime(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }

        String normalized = raw.trim();
        try {
            return LocalTime.parse(normalized);
        } catch (DateTimeParseException ignored) {
            // Fall through to 12-hour parsing.
        }

        try {
            return LocalTime.parse(normalized.toUpperCase(Locale.ENGLISH), DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH));
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private int parseIntOrDefault(String raw, int fallback) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private String selectedTimeValue(ComboBox<String> box) {
        return box == null || box.getValue() == null ? "" : box.getValue();
    }

    private void bindEndTimeAfterStart(ComboBox<String> startBox, ComboBox<String> endBox) {
        startBox.setOnAction(e -> {
            String startVal = selectedTimeValue(startBox);
            if (startVal.isBlank()) {
                return;
            }
            try {
                LocalTime start = LocalTime.parse(startVal, DateTimeFormatter.ofPattern("h:mm a"));
                LocalTime end = start.plusHours(1);
                endBox.setValue(end.format(DateTimeFormatter.ofPattern("h:mm a")));
            } catch (Exception ex) {
                // Silently ignore parse errors
            }
        });
    }

    private static final class EventEditInput {
        private final String title;
        private final String description;
        private final LocalDate date;
        private final String startTime;
        private final String endTime;
        private final boolean recurring;

        private EventEditInput(String title, String description, LocalDate date, String startTime, String endTime, boolean recurring) {
            this.title = title;
            this.description = description;
            this.date = date;
            this.startTime = startTime;
            this.endTime = endTime;
            this.recurring = recurring;
        }
    }

    // Record for quick add dialog input
    record QuickAddEventInput(String title, String description, String startTime, String endTime, boolean recurring, boolean autoReminder) {}
}
