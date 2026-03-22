/*
Stores Events, UserActions, and ProgressBars in program memory. Handles saving and loading events and progress bars.
Allows for Events, ProgressBars, and undo/redo functionality to be accessed.

Consider breaking up functionality.
 */

import java.util.ArrayList;

public class CalendarBook implements UserInputValidation {
    private String timezone;
    private String currentView;
    private ArrayList<Event> eventArray;
    private ArrayList<UserAction> undoStack;
    private ArrayList<UserAction> redoStack;
    private ArrayList<ProgressBar> progrssBarArray;

    // Boilerplate

    // The ArrayLists are initalized as empty when a CalendarBook is constructed.
    public CalendarBook(String timezone, String currentView) {
        this.timezone = timezone;
        this.currentView = currentView;
        this.eventArray = new ArrayList<Event>();
        this.undoStack = new ArrayList<UserAction>();
        this.redoStack = new ArrayList<UserAction>();
        this.progrssBarArray = new ArrayList<ProgressBar>();
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public String getCurrentView() {
        return currentView;
    }

    public void setCurrentView(String currentView) {
        this.currentView = currentView;
    }

    public ArrayList<Event> getEventArray() {
        return eventArray;
    }

    public ArrayList<UserAction> getUndoStack() {
        return undoStack;
    }

    public ArrayList<UserAction> getRedoStack() {
        return redoStack;
    }

    public ArrayList<ProgressBar> getProgrssBarArray() {
        return progrssBarArray;
    }

    // The good stuff

    public int createEvent() {
        return index;
    }

    public void deleteEvent(int index) {}

    public void editEvent(int index) {}

    public Event getEvent() {}

    public ArrayList<Event> createSummary(boolean upcoming) {}

    public int createProgressBar() {}

    public void editProgressBar(int index) {}

    public void deleteProgressBar(int index) {}

    public ProgressBar getProgressBar(int index) {}

    // Loads event data to eventArray from CSV file.
    public void loadEventData() {}

    // Saves event data to CSV file from eventArray.
    public void saveEventData() {}

    // Exports associated data to a custom CSV file.
    public void exportData(String destination) {}

    // Saves progress bar data to CSV file.
    public void saveProgressBarData() {}

    // Loads progress bar data from CSV file.
    public void loadProgressBarData() {}

    // Undoes the UserAction at the top of the undoStack and moves it to the redoStack
    public void undo() {}

    // Redoes the UserAction on top of the redoStack and moves it to the undoStack
    public void redo() {}

}
