package org;

import java.util.Scanner;

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
public class Main {
    public static void main(String[] args) {

        // Main is intentionally thin: route user choice to auth/event services.
        System.out.println("Welcome to the user account and event system.");
        Scanner userInput = new Scanner(System.in);

        // First decision: existing account login vs new account creation.
        System.out.println("Type 'login' to sign in or 'create' to make a new account:");
        String mode = userInput.nextLine().trim().toLowerCase();

        if (mode.equals("login")) {
            // Authenticate from users.csv and return the loaded account object.
            UserAccount loggedInUser = AuthService.loginFlow(userInput);
            if (loggedInUser == null) {
                return;
            }

            // Optional event creation after successful login.
            boolean shouldCreateEvent = readYesNo(userInput, "Create an event now? (yes/no): ");
            if (shouldCreateEvent) {
                EventService.createEventFlow(userInput, loggedInUser);
            }
            return;
        }

        if (!mode.equals("create")) {
            System.out.println("Unknown option. Please restart and type 'login' or 'create'.");
            return;
        }

        // Create and persist a brand new account in users.csv.
        UserAccount createdUser = AuthService.createAccountFlow(userInput);
        if (createdUser == null) {
            return;
        }

        // Optional event creation after account registration.
        boolean shouldCreateEvent = readYesNo(userInput, "Create an event now? (yes/no): ");
        if (shouldCreateEvent) {
            EventService.createEventFlow(userInput, createdUser);
        }
    }

    // Reads yes/no style input and returns true for yes and false for no.
    private static boolean readYesNo(Scanner scanner, String prompt) {
        while (true) {
            System.out.println(prompt);
            String value = scanner.nextLine().trim().toLowerCase();

            if (value.equals("yes") || value.equals("y")) {
                return true;
            }
            if (value.equals("no") || value.equals("n")) {
                return false;
            }

            System.out.println("Please enter yes or no.");
        }
    }
}
