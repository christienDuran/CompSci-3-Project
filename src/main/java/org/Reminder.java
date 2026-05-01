package org;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/*
Reminders are notifications that pop up to the user to remind them of important events. Reminders are created by the
org.CalendarBook and associated with an org.Event. They are displayed by the UI.

Reminder lifecycle:
- recalculateTrigger(Event) sets the alert time from the event start.
- isDue(now) checks whether the reminder should fire.
- sendAlert(...) marks the reminder as fired after displaying the notification.
- snoozeAlert(...) moves the trigger forward and clears the fired state.
 */
public class Reminder {

    // Shared formatter used by console alerts.
    private static final DateTimeFormatter ALERT_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd h:mm a");

    // Unique ID for this reminder entry.
    private int reminderId;
    // Event ID this reminder is attached to.
    private int eventId;
    // Cached event description shown in reminder alerts.
    private String eventDescription;
    // Reminder lead time before event start.
    private int minutesBefore;
    // Default snooze duration in minutes.
    private int snoozeLength;
    // Absolute date-time when the alert should fire.
    private LocalDateTime triggerAt;
    // Soft on/off switch for reminders without deleting them.
    private boolean active;
    // Tracks whether this reminder has already fired for its current trigger.
    private boolean fired;

    public Reminder(int reminderId, int eventId, int minutesBefore, int snoozeLength) {
        if (minutesBefore < 0) {
            throw new IllegalArgumentException("minutesBefore cannot be negative.");
        }
        if (snoozeLength < 0) {
            throw new IllegalArgumentException("snoozeLength cannot be negative.");
        }

        this.reminderId = reminderId;
        this.eventId = eventId;
        this.eventDescription = "";
        this.minutesBefore = minutesBefore;
        this.snoozeLength = snoozeLength;
        this.active = true;
        this.fired = false;
    }

    public Reminder(int reminderId, int eventId, String eventDescription, int minutesBefore, int snoozeLength) {
        this(reminderId, eventId, minutesBefore, snoozeLength);
        this.eventDescription = (eventDescription == null) ? "" : eventDescription;
    }

    /**
     * Recomputes trigger time from the event start using this reminder's offset.
     * Also refreshes the stored event description so alert text stays current.
     */
    public void recalculateTrigger(Event event) {
        recalculateTrigger(event, LocalDateTime.now());
    }

    public void recalculateTrigger(Event event, LocalDateTime referenceTime) {
        if (event == null) {
            throw new IllegalArgumentException("event cannot be null.");
        }
        if (event.getDate() == null || event.getStartTime() == null) {
            throw new IllegalStateException("Event must have a date and start time.");
        }

        LocalDateTime anchor = referenceTime == null ? LocalDateTime.now() : referenceTime;
        LocalDateTime threshold = anchor.plusMinutes(minutesBefore);
        LocalDateTime occurrenceStart = EventService.nextOccurrenceStartOnOrAfter(event, threshold);

        if (occurrenceStart == null) {
            this.triggerAt = null;
            this.fired = true;
            this.eventDescription = (event.getDescription() == null) ? "" : event.getDescription();
            return;
        }

        this.triggerAt = occurrenceStart.minusMinutes(minutesBefore);
        this.eventDescription = (event.getDescription() == null) ? "" : event.getDescription();
        this.fired = false;
    }

    /**
     * Returns true only during the exact trigger minute and only if not already fired.
     */
    public boolean isDue(LocalDateTime now) {
        if (now == null || triggerAt == null) {
            return false;
        }

        LocalDateTime nowMinute = now.withSecond(0).withNano(0);
        LocalDateTime triggerMinute = triggerAt.withSecond(0).withNano(0);
        return active && !fired && nowMinute.isEqual(triggerMinute);
    }

    /**
     * Sends a basic alert message and marks this reminder as fired.
     */
    public void sendAlert() {
        if (triggerAt == null) {
            System.out.println("Reminder " + reminderId + " has no trigger time set.");
            return;
        }

        String description = eventDescription.isBlank() ? "No description." : eventDescription;
        System.out.println("[REMINDER] Event " + eventId + " starts soon. Trigger time: "
            + triggerAt.format(ALERT_TIME_FORMAT)
            + ". Description: " + description);
        this.fired = true;
    }

    /**
     * Sends an alert using a friendly event title and marks this reminder as fired.
     */
    public void sendAlert(String eventTitle) {
        String title = (eventTitle == null || eventTitle.isBlank()) ? "Untitled Event" : eventTitle;
        if (triggerAt == null) {
            System.out.println("[REMINDER] " + title + " (trigger time not set)");
            return;
        }

        String description = eventDescription.isBlank() ? "No description." : eventDescription;
        System.out.println("[REMINDER] " + title + " starts soon at "
            + triggerAt.format(ALERT_TIME_FORMAT)
            + ". Description: " + description);
        this.fired = true;
    }

    /**
     * Snoozes using this reminder's configured snooze length.
     */
    public void snoozeAlert() {
        if (!active) {
            return;
        }
        if (snoozeLength <= 0) {
            throw new IllegalStateException("snoozeLength must be greater than zero to snooze.");
        }

        this.triggerAt = LocalDateTime.now().plusMinutes(snoozeLength);
        this.fired = false;
    }

    /**
     * Snoozes using a custom duration and updates the default snooze length.
     */
    public void snoozeAlert(int customSnoozeMinutes) {
        if (customSnoozeMinutes <= 0) {
            throw new IllegalArgumentException("customSnoozeMinutes must be greater than zero.");
        }

        this.snoozeLength = customSnoozeMinutes;
        this.triggerAt = LocalDateTime.now().plusMinutes(customSnoozeMinutes);
        this.fired = false;
    }

    public void disable() {
        this.active = false;
    }

    public void enable() {
        this.active = true;
    }

    public int getReminderId() {
        return reminderId;
    }

    public void setReminderId(int reminderId) {
        this.reminderId = reminderId;
    }

    public int getEventId() {
        return eventId;
    }

    public void setEventId(int eventId) {
        this.eventId = eventId;
    }

    public int getMinutesBefore() {
        return minutesBefore;
    }

    public String getEventDescription() {
        return eventDescription;
    }

    public void setEventDescription(String eventDescription) {
        this.eventDescription = (eventDescription == null) ? "" : eventDescription;
    }

    public void setMinutesBefore(int minutesBefore) {
        if (minutesBefore < 0) {
            throw new IllegalArgumentException("minutesBefore cannot be negative.");
        }
        this.minutesBefore = minutesBefore;
    }

    public int getSnoozeLength() {
        return snoozeLength;
    }

    public void setSnoozeLength(int snoozeLength) {
        if (snoozeLength < 0) {
            throw new IllegalArgumentException("snoozeLength cannot be negative.");
        }
        this.snoozeLength = snoozeLength;
    }

    public LocalDateTime getTriggerAt() {
        return triggerAt;
    }

    public void setTriggerAt(LocalDateTime triggerAt) {
        this.triggerAt = triggerAt;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isFired() {
        return fired;
    }

    public void setFired(boolean fired) {
        this.fired = fired;
    }

    @Override
    public String toString() {
        return "Reminder{" +
                "reminderId=" + reminderId +
                ", eventId=" + eventId +
                ", eventDescription='" + eventDescription + '\'' +
                ", minutesBefore=" + minutesBefore +
                ", snoozeLength=" + snoozeLength +
                ", triggerAt=" + triggerAt +
                ", active=" + active +
                ", fired=" + fired +
                '}';
    }
}
