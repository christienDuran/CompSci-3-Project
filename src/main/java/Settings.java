/*
Singleton for global application settings management. Stores current settings allowing other classes to refer to them to
change their behavior. Allows the settings to be changed by other classes, usually due to user input.
 */
public class Settings {
    int theme;
    String windowSize;
    String timeFormat;
    boolean sideBarOpen;


    void changeTheme(int theme) {

    }

    void changeWindowSize(String windowSize) {}

    void changeTimeFormat(String timeFormat) {}

    void changeSideBarOpen(boolean sideBarOpen) {}


}
