package org;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

/*
Owns reminder polling and due-notification popup behavior for the app.
*/
public class ReminderNotifier {
    private final Supplier<Stage> ownerStageSupplier;
    private final Supplier<UserAccount> userSupplier;
    private final Supplier<List<Reminder>> remindersSupplier;
    private final Function<Integer, Event> eventResolver;
    private final Runnable onReminderStateChanged;
    private final Supplier<DateTimeFormatter> timeFormatterSupplier;

    private Timeline reminderPoller;
    private boolean reminderNotificationOpen;

    public ReminderNotifier(
        Supplier<Stage> ownerStageSupplier,
        Supplier<UserAccount> userSupplier,
        Supplier<List<Reminder>> remindersSupplier,
        Function<Integer, Event> eventResolver,
        Runnable onReminderStateChanged,
        Supplier<DateTimeFormatter> timeFormatterSupplier
    ) {
        this.ownerStageSupplier = ownerStageSupplier;
        this.userSupplier = userSupplier;
        this.remindersSupplier = remindersSupplier;
        this.eventResolver = eventResolver;
        this.onReminderStateChanged = onReminderStateChanged;
        this.timeFormatterSupplier = timeFormatterSupplier;
    }

    public void start() {
        stop();

        reminderPoller = new Timeline(new KeyFrame(Duration.seconds(5), e -> checkDueReminders()));
        reminderPoller.setCycleCount(Timeline.INDEFINITE);
        reminderPoller.play();

        checkDueReminders();
    }

    public void stop() {
        if (reminderPoller != null) {
            reminderPoller.stop();
            reminderPoller = null;
        }
    }

    public void resetState() {
        reminderNotificationOpen = false;
    }

    private void checkDueReminders() {
        UserAccount currentUser = userSupplier.get();
        List<Reminder> reminders = remindersSupplier.get();

        if (currentUser == null || reminders == null || reminders.isEmpty() || reminderNotificationOpen) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();

        for (Reminder reminder : reminders) {
            if (!reminder.isDue(now)) {
                continue;
            }

            reminder.setFired(true);
            CsvStorage.saveRemindersForUser(currentUser.getAccountID(), reminders);

            Event event = eventResolver.apply(reminder.getEventId());
            showReminderNotification(reminder, event);
            onReminderStateChanged.run();
            break;
        }
    }

    private void showReminderNotification(Reminder reminder, Event event) {
        String title = (event == null || event.getTitle() == null || event.getTitle().isBlank())
            ? "Untitled Event"
            : event.getTitle();

        Stage ownerStage = ownerStageSupplier.get();
        Stage notificationStage = new Stage(StageStyle.DECORATED);
        if (ownerStage != null) {
            notificationStage.initOwner(ownerStage);
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
            if (ownerStage != null) {
                double x = ownerStage.getX() + Math.max(0, ownerStage.getWidth() - notificationStage.getWidth() - 24);
                double y = ownerStage.getY() + 70;
                notificationStage.setX(x);
                notificationStage.setY(y);
            }
        });

        notificationStage.setOnHiding(e -> {
            reminderNotificationOpen = false;
            onReminderStateChanged.run();
            UserAccount currentUser = userSupplier.get();
            List<Reminder> reminders = remindersSupplier.get();
            if (currentUser != null && reminders != null) {
                CsvStorage.saveRemindersForUser(currentUser.getAccountID(), reminders);
            }
        });

        notificationStage.show();
    }

    private String buildReminderMessage(Reminder reminder, Event event) {
        StringBuilder sb = new StringBuilder();
        sb.append("Event ID: ").append(reminder.getEventId()).append("\n");
        sb.append("Minutes before: ").append(reminder.getMinutesBefore()).append("\n");
        sb.append("Action required: Dismiss this notification when acknowledged.\n");

        if (event != null) {
            sb.append("Date: ").append(event.getDate()).append("\n");
            DateTimeFormatter formatter = timeFormatterSupplier.get();
            sb.append("Time: ").append(event.getStartTime().format(formatter))
                .append(" - ")
                .append(event.getEndTime().format(formatter))
                .append("\n");
            sb.append("Description: ")
                .append(event.getDescription() == null || event.getDescription().isBlank() ? "No description." : event.getDescription());
        }

        return sb.toString();
    }
}
