package example;// When the app opens up, we want the user to be able to click create account
//          after clicking, they will be prompted to create a username and a password.

// When they make their username and password, an accountID number should be randomly generated(which will be 5 numbers)
// The accountID, username, and password will then be stored in a csv file

// The next step will be, having a pop-up Questionnaire asking the user about wanting recurring Events stored-
//  within their CalendarBook


import java.util.Random;
import java.io.FileWriter;
import java.io.IOException;


public class UserAccount {

    // Fields
    private String username;
    private String password;
    private int accountID;           // This will be randomly generated and not provided by the User
    private boolean isLoggedIn;

    // Constructor
    public UserAccount(String username, String password, int accountID) {
        this.username = username;
        this.password = password;  // Security vulnerability
        this.accountID = accountID;
        this.isLoggedIn = false;
    }

    // Create Account,
    public static UserAccount createAccount(String username, String password) {
        Random rand = new Random();
        int accountID = 10000 + rand.nextInt(90000); // ensures 5-digit number
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


    //Saving the user's credentials to a csv file, but has no duplicate checking or validation
    public void saveToCSV() {
        try (FileWriter writer = new FileWriter("users.csv", true)) {
            writer.append(accountID + "," + username + "," + password + "\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // We need to be able to allow a user to login when there account already exists
    //Load users from file
    //Match username/password

    // WE also need to be able to  Hide passwords (basic security)
    // like john,hashed_password

    // Getters
    public String getUsername() {
        return username;
    }

    public int getAccountID() {
        return accountID;
    }


}



