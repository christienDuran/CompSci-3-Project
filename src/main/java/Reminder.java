/*
Reminders are notifications that pop up to the user to remind them of important events. Reminders are created by the
CalendarBook and associated with an Event. They are displayed by the UI.
 */
public class Reminder {


    int reminderTime; // will be in format like 1130 or 1530
    int snoozeLength; // in minutes most likely
    String reminderDay; // maybe string for this? not sure yet
    String type;  // event type

    void sendAlert() {

    }

    void snoozeAlert() {

    }
}
