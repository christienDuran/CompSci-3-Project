package org.example;



public class UserAccount {

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




