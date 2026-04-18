package org;

/*
Stores a single user action which can be undone or redone. Includes the type of action and all information associated
with it. Logic for completeing the undo/redo operation is kept in UnRedoer.
 */
public class UserAction {

    // having enum, creates predefined options for our system
    //Enums make sure your system only works with valid, known values, meaning there is a
    // controlled set of constants our program will agree upon
    public enum ActionType {
        CREATE_EVENT,
        EDIT_EVENT,
        DELETE_EVENT,
        create_ProgressBar,
        delete_ProgressBar,
        edit_ProgressBar,
    }
    // Note:  enumeration constants  should all be UPPERCASE!
    //          enumeration methods should be lowercase

    private ActionType type;

    //For the event actions
    private Event oldEvent;
    private Event newEvent;

    //  Progress bar actions if we want to use functionality
//    private ProgressBar oldProgressBar;
//    private ProgressBar newProgressBar;

    // Constructor for CREATE / DELETE
    public UserAction(ActionType type, Event event) {
        this.type = type;

        if (type == ActionType.CREATE_EVENT) {
            this.newEvent = event;
        } else if (type == ActionType.DELETE_EVENT) {
            this.oldEvent = event;
        }
    }

    // Constructor for EDIT
    public UserAction(ActionType type, Event oldEvent, Event newEvent) {
        this.type = type;
        this.oldEvent = oldEvent;
        this.newEvent = newEvent;
    }

    // Getters
    public ActionType getType() {
        return type;
    }

    public Event getOldEvent() {
        return oldEvent;
    }

    public Event getNewEvent() {
        return newEvent;
    }

//    public ProgressBar getOldProgressBar() {
//        return oldProgressBar;
//    }
//
//    public ProgressBar getNewProgressBar() {
//        return newProgressBar;
//    }
}




