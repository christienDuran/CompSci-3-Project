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


}
