package org;/*
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

    // The ArrayLists are initalized as empty when a org.CalendarBook is constructed.
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
    // In order to assist with deleting events, each created event should have an ID
    // event IDs can be generated when the event is created. This can help with deleting too,
    // I looked up that it would be safer(error prevention)

    public Event createEvent(Event event){
        eventArray.add(event);

        //
        undoStack.add(new UserAction("CREATED_EVENT", event));
        redoStack.clear();

        return event;
    }

    public void deleteEvent(int eventId){
        Event toRemove = null;

        //can't remove an object without iterating. We don't want to get a ConcurrentModificationException
        // We find the object then,
        // if the object within array equals 'eventId', the object will equal 'toRemove'
        // So, we are using loop to find the object, store it into a 'toRemove' and then remove the object after
        // the loop ends
        for (Event e : eventArray) {

            if (e.getId() == eventId) {
                toRemove = e;
                break;
            }
        }
        // might not know if the event exists yet, we initialize to null(prevent errors)
        if (toRemove != null) {
            eventArray.remove(toRemove);

            undoStack.add(new UserAction("DELETED_EVENT", toRemove));
            redoStack.clear();
        }
    }


    // need for the system to grab the event's Id, so the credentials of the event to
    // be changed
    public void editEvent(int eventId, Event updatedEvent){
        for( int i = 0; i < eventArray.size(); i++ ){
            Event oldEvent = eventArray.get(i);

            // if the Ids match, make the oldEvent credentials change to match the updatedEvent( that
            // comes from the user)
            if (oldEvent.getId() == eventId){
                eventArray.set(i, updatedEvent);

                //Also oldEvent acts as a screenshot before an edit, so it will let the user reverse a change
                undoStack.add(new UserAction("EDITED_EVENT", oldEvent));
                redoStack.clear(); // the redo and undo stack portion, I had to look-up and still don't quite comprehend,
                return;             // but know that it functions like a timeline
                // involving the 'return' after finding the correct event and updating, the looping process stops
            }
        }
    }




    // Temporary stubs keep class compilable until these features are implemented.
    public Event getEvent() { return null; }

    public ArrayList<Event> createSummary(boolean upcoming) { return new ArrayList<Event>(); }

    public int createProgressBar() { return -1; }

    public void editProgressBar(int index) {}

    public void deleteProgressBar(int index) {}

    public ProgressBar getProgressBar(int index) { return null; }

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

    // Undoes the org.UserAction at the top of the undoStack and moves it to the redoStack
    public void undo() {}

    // Redoes the org.UserAction on top of the redoStack and moves it to the undoStack
    public void redo() {}

}
