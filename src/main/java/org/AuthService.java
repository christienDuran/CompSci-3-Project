package org;

import java.util.Scanner;

/**
 * Handles account registration and login prompt flows.
 * Keeps user-input orchestration outside Main.
 */
public final class AuthService {

    private AuthService() {
    }

    // Registration flow:
    // 1) read username/password, 2) reject duplicates, 3) create and persist account.
    // Returns the created account or null when registration cannot continue.
    public static UserAccount createAccountFlow(Scanner userInput) {
        System.out.println("Enter Username: ");
        String username = userInput.nextLine();

        if (CsvStorage.usernameExists(username)) {
            System.out.println("That username already exists. Please restart and choose a different username.");
            return null;
        }

        System.out.println(" Now enter your password: ");
        String password = userInput.nextLine();

        UserAccount user = UserAccount.createAccount(username, password);
        user.saveToCSV();
        System.out.println("Account created! Your ID is: " + user.getAccountID());
        return user;
    }

    // Login flow:
    // 1) collect credentials, 2) authenticate against users.csv, 3) return loaded account.
    // Returns null if credentials do not match a stored account.
    public static UserAccount loginFlow(Scanner userInput) {
        System.out.println("Enter Username: ");
        String username = userInput.nextLine();

        System.out.println("Enter your password: ");
        String password = userInput.nextLine();

        UserAccount user = UserAccount.loginFromCSV(username, password);
        if (user == null) {
            return null;
        }

        System.out.println("Welcome back, " + user.getUsername() + ". Your ID is: " + user.getAccountID());
        return user;
    }
}
