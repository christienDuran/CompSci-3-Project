
import java.time.*;
import java.util.ArrayList;
import java.util.Scanner;
import java.sql.SQLOutput;

public class Main {

}

// is supposed to allow the User to track their finances.
// Are we going to allow the user to add their own fields like "Food", "Mortgage", "Groceries", or are we going to add it?
class Budget {
    private String expenseName;
    private double amount;
    private LocalDate date; // allow the user to set a time when an expense needs to be paid

    // Constructor
    public Budget(String expenseName, double amount, LocalDate date) {
        this.expenseName = expenseName;
        this.amount = amount;
        this.date = date;
    }

    //Methods
}

/*
Stores Events, UserActions, and ProgressBars in program memory. Handles saving and loading events and progress bars.
Allows for Events, ProgressBars, and undo/redo functionality to be accessed.

Consider breaking up functionality.
 */
class CalendarBook implements UserInputValidation {
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

/*
Template for storing data for a calendar event. Generic superclass for more specific event types,
 */
class Event implements TimeTracking {
    int id;
    String tittle;
    String description;
    LocalDate date;
    LocalTime startTime;
    LocalTime endTime;
    boolean recurring;

    //Interface methods

    public void setTime(LocalTime time) {
        this.startTime = time;
    }
    public void setDate(LocalDate date){
        this.date = date;
    }
    public LocalTime getTime(){
        return this.startTime;
    }
    public LocalDate getDate(){
        return  this.date;
    }

    //Prefered way of editing an existing event.

    public void editEvent(String newTittle, String newDescription, LocalDate newDate, LocalTime newStartTime, LocalTime newEndTime, boolean recurring) {

    }

    // Boilerplate

    public Event(int id, String tittle, String description, LocalDate date, LocalTime startTime, LocalTime endTime, boolean recurring) {
        this.id = id;
        this.tittle = tittle;
        this.description = description;
        this.date = date;
        this.startTime = startTime;
        this.endTime = endTime;
        this.recurring = recurring;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getTittle() {
        return tittle;
    }

    public void setTittle(String tittle) {
        this.tittle = tittle;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalTime endTime) {
        this.endTime = endTime;
    }

    public boolean isRecurring() {
        return recurring;
    }

    public void setRecurring(boolean recurring) {
        this.recurring = recurring;
    }
}

class Goal {
}

class ProgressBar {
}

class Reminder {

    int reminderTime; // will be in format like 1130 or 1530
    int snoozeLength; // in minutes most likely
    String reminderDay; // maybe string for this? not sure yet
    String type;  // event type

    void sendAlert() {

    }

    void snoozeAlert() {

    }
}

class Settings {
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

/*
Interface for handling date and time information for objects which either keep track of the current date/time or have a
date/time associated with them.
 */
interface TimeTracking {
    public void setTime(LocalTime time);
    public void setDate(LocalDate date);
    public LocalTime getTime();
    public LocalDate getDate();
}

class User {
}

class UserAccount {

    // Fields
    private String username;
    private String password;
    private int accountID;
    private boolean isLoggedIn;

    // Constructor
    public UserAccount(String username, String password, int accountID) {
        this.username = username;
        this.password = password;
        this.accountID = accountID;
        this.isLoggedIn = false;
    }

    // Create Account
    public static UserAccount createAccount(String username, String password, int accountID) {
        return new UserAccount(username, password, accountID);
    }

    // Login method
    public boolean login(String username, String password) {
        if (this.username.equals(username) && this.password.equals(password)) {
            isLoggedIn = true;
            System.out.println("Login successful.");
            return true;
        } else {
            System.out.println("Invalid username or password.");
            return false;
        }
    }

    // Logout method
    public void logout() {
        if (isLoggedIn) {
            isLoggedIn = false;
            System.out.println("Logged out successfully.");
        } else {
            System.out.println("User is not logged in.");
        }
    }

    // Getters (might use for something)
    public String getUsername() {
        return username;
    }

    public int getAccountID() {
        return accountID;
    }
}

class UserAction {
}

interface UserInputValidation {
}

class WeatherService {

    double longitude;  // variables for location can be changed
    double latitude;   // latitude and longitude is good for right now

    double[] forecastData;  // we could have separate arrays for temp whether its sunny or cloudy chance of rain

    void getWeather() {

    }

    void getForecastData() {

    }

}

class Questionnaire {
    private Scanner userInput = new Scanner(System.in);

    //Ask Yes/No question
    public  boolean askRecurring(){
        System.out.println("Do you want recurring events (Y/N) ");
        String answer = userInput.nextLine();

        //the .equalsIgnoreCase makes the system not care if input is lowercase or upper. Anything else will be false
        if(answer.equalsIgnoreCase("Y")){
            return true;
        }else {
            return false;
        }
    }

    public QuestionnaireEvent createEvent(){
        System.out.println(" Enter event details: ");

        System.out.println("\n Title :");
        String title = userInput.nextLine();

        System.out.println("\n Day: ");
        String day = userInput.nextLine();

        System.out.println("\n Set Time: ");  // will be implemented by something else
        String time = userInput.nextLine();

        System.out.println("\n Recurrence daily/weekly/monthly:  ");
        String recurrence = userInput.nextLine();

        return new QuestionnaireEvent(title, day, time, recurrence);

    }

    public ArrayList<QuestionnaireEvent> run(){ // run is a method name "ArrayList<Event>" , return a list of Event objects
        ArrayList<QuestionnaireEvent> recurrEvents = new ArrayList<QuestionnaireEvent>(); // prepares an empty container to store recurring events

        if(askRecurring()){
            QuestionnaireEvent event = createEvent(); // creates a new event from gathering (Title, Day, Time, when event recurs
            recurrEvents.add(event);  // adds to the empty Questionnaire list, that will add to out "main" event class,
        }else {                         // which will then be displayed to the user's calendar
            System.out.println(" Skipped Questionnaire");
        }
            return recurrEvents;  // return the user to

    }
}

class QuestionnaireEvent {
    String title;
    String day;
    String time; // will be implemented by Timeinterface
    String recurrence;

    public QuestionnaireEvent(String title, String day, String time, String recurrence){
        this.title = title;
        this.day = day;
        this.time = time;
        this.recurrence = recurrence;
    }
}