package org;

// UserAccount stores one user's credentials and account identity.
// New accounts are created from user input, then added to users.csv.
// Existing accounts are loaded from users.csv for login validation.
// Each account ID is used to link that user to their events in events.csv.

import java.util.List;


/**
 * Represents a single user account in the system.
 * Holds account identity/login state and delegates CSV persistence operations.
 */
public class UserAccount {

    // The unique username chosen by the user.
    private String username;
    // Plain-text password stored for simple local use in this project.
    private String password;
    // Numeric account identifier used to link records across CSV files.
    private int accountID;           // This will be randomly generated and not provided by the User
    // Tracks whether this in-memory user object is currently logged in.
    private boolean isLoggedIn;

    // Constructor used by account creation and CSV loading helpers.
    public UserAccount(String username, String password, int accountID) {
        this.username = username;
        this.password = password;
        this.accountID = accountID;
        this.isLoggedIn = false;
    }

    // Factory method for account creation.
    // It allocates the next account ID and stores the provided password directly.
    public static UserAccount createAccount(String username, String password) {
        int accountID = CsvStorage.nextAccountId();
        return new UserAccount(username, password, accountID);
    }

    // Loads account row from users.csv and validates incoming credentials.
    // Returns the loaded account on success, or null on failed authentication.
    public static UserAccount loginFromCSV(String username, String password) {
        UserAccount storedUser = CsvStorage.findUserByUsername(username);
        if (storedUser == null) {
            System.out.println("Invalid username or password.");
            return null;
        }

        if (!storedUser.password.equals(password)) {
            System.out.println("Invalid username or password.");
            return null;
        }

        storedUser.isLoggedIn = true;
        System.out.println("Login successful.");
        return storedUser;
    }





    // In-memory login check for this already-instantiated account object.
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

    // Clears login state for this account instance.
    public void logout() {
        if (isLoggedIn) {
            isLoggedIn = false;
            System.out.println("Logged out successfully.");
        } else {
            System.out.println("User is not logged in.");
        }
    }


    // Saves account info so the user is added to users.csv.
    // Duplicate username checks happen inside CsvStorage.
    public void saveToCSV() {
        CsvStorage.saveUser(this);
    }

    // Saves one event row for this specific account into events.csv.
    public void saveEventToCSV(Event event) {
        CsvStorage.saveEventForUser(this.accountID, event);
    }

    // Loads all events owned by this account from events.csv.
    public List<Event> loadMyEvents() {
        return CsvStorage.loadEventsForUser(this.accountID);
    }

    // We need to be able to allow a user to login when their account already exists.
    // Load users from file and match username/password.

    // We also need a basic way to hide passwords in storage.
    // Example format: john,hashed_password

    // Returns the account username.
    public String getUsername() {
        return username;
    }

    // Returns this account's numeric ID.
    public int getAccountID() {
        return accountID;
    }

    // Returns the stored plain-text password.
    public String getPassword() {
        return password;
    }

}



