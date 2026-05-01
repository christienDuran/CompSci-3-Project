package org;

/*
Stores a single user action which can be undone or redone. Includes the type of action and all information associated
with it. Logic for completeing the undo/redo operation is kept in UnRedoer.
 */
public class UserAction {

    // having enum, creates predefined options for our system
    //Enums make sure your system only works with valid, known values, meaning there is a
    // controlled set of constants our program will agree upon
    public enum actionType {
        create_Event,
        edit_Event,
        delete_Event,
        create_ProgressBar,
        delete_ProgressBar,
        edit_ProgressBar,
    }

    private actionType type;
    private Event event;

    public UserAction(actionType type, Event event) {
        this.type = type;
        this.event = event;
    }

    public UserAction(String typeName, Event event) {
        this(toActionType(typeName), event);
    }

    // Keeps existing CalendarBook string calls working while normalizing to enum values.
    private static actionType toActionType(String typeName) {
        if (typeName == null) {
            throw new IllegalArgumentException("Action type cannot be null.");
        }

        return switch (typeName.trim().toUpperCase()) {
            case "CREATED_EVENT" -> actionType.create_Event;
            case "EDITED_EVENT" -> actionType.edit_Event;
            case "DELETED_EVENT" -> actionType.delete_Event;
            case "CREATE_PROGRESSBAR" -> actionType.create_ProgressBar;
            case "EDIT_PROGRESSBAR" -> actionType.edit_ProgressBar;
            case "DELETE_PROGRESSBAR" -> actionType.delete_ProgressBar;
            default -> throw new IllegalArgumentException("Unknown action type: " + typeName);
        };
    }

    public actionType getType() {
        return type;
    }

    public Event getEvent() {
        return event;
    }


}
