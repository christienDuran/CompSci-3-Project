package org;

import java.util.Scanner;

/**
 * Handles account registration and login prompt flows.
 * Keeps user-input orchestration outside Main.
 */
public final class AuthService {

    private AuthService() {
    }

    // UI/Service entry point for account creation.
    public static UserAccount createAccount(String username, String password) {
        String normalizedUsername = username == null ? "" : username.trim();
        String normalizedPassword = password == null ? "" : password.trim();

        if (normalizedUsername.isEmpty()) {
            throw new IllegalArgumentException("Username cannot be empty.");
        }
        if (normalizedPassword.isEmpty()) {
            throw new IllegalArgumentException("Password cannot be empty.");
        }

        if (CsvStorage.usernameExists(normalizedUsername)) {
            throw new IllegalArgumentException("That username already exists.");
        }

        UserAccount user = UserAccount.createAccount(normalizedUsername, normalizedPassword);
        user.saveToCSV();
        return user;
    }

    // UI/Service entry point for login.
    public static UserAccount login(String username, String password) {
        String normalizedUsername = username == null ? "" : username.trim();
        String normalizedPassword = password == null ? "" : password;

        if (normalizedUsername.isEmpty() || normalizedPassword.isEmpty()) {
            throw new IllegalArgumentException("Username and password are required.");
        }

        UserAccount user = UserAccount.loginFromCSV(normalizedUsername, normalizedPassword);
        if (user == null) {
            throw new IllegalArgumentException("Invalid username or password.");
        }

        return user;
    }

    // Registration flow:
    // 1) read username/password, 2) reject duplicates, 3) create and persist account.
    // Returns the created account or null when registration cannot continue.
    public static UserAccount createAccountFlow(Scanner userInput) {
        System.out.println("Enter Username: ");
        String username = userInput.nextLine();

        System.out.println(" Now enter your password: ");
        String password = userInput.nextLine();

        try {
            UserAccount user = createAccount(username, password);
            System.out.println("Account created! Your ID is: " + user.getAccountID());
            return user;
        } catch (IllegalArgumentException ex) {
            System.out.println(ex.getMessage());
            return null;
        }
    }

    // Login flow:
    // 1) collect credentials, 2) authenticate against users.csv, 3) return loaded account.
    // Returns null if credentials do not match a stored account.
    public static UserAccount loginFlow(Scanner userInput) {
        System.out.println("Enter Username: ");
        String username = userInput.nextLine();

        System.out.println("Enter your password: ");
        String password = userInput.nextLine();

        try {
            UserAccount user = login(username, password);
            System.out.println("Welcome back, " + user.getUsername() + ". Your ID is: " + user.getAccountID());
            return user;
        } catch (IllegalArgumentException ex) {
            System.out.println(ex.getMessage());
            return null;
        }
    }
}
