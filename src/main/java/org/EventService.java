package org;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Scanner;

/**
 * Handles event creation prompts, validation, and persistence.
 */
public final class EventService {

    private EventService() {
    }

    // Event creation flow:
    // 1) gather event fields, 2) validate times, 3) save event for the current account.
    public static void createEventFlow(Scanner userInput, UserAccount user) {
        System.out.println("Event title: ");
        String title = userInput.nextLine();

        System.out.println("Event description: ");
        String description = userInput.nextLine();

        LocalDate date = readDate(userInput);
        LocalTime start = readTime(userInput, "Start time (HH:mm): ");
        LocalTime end = readTime(userInput, "End time (HH:mm): ");

        while (end.isBefore(start)) {
            System.out.println("End time cannot be before start time. Please enter end time again.");
            end = readTime(userInput, "End time (HH:mm): ");
        }

        System.out.println("Is this recurring? (true/false): ");
        boolean recurring = Boolean.parseBoolean(userInput.nextLine().trim());

        // Event ID is passed as 0; CsvStorage assigns the next available ID.
        Event event = new Event(0, title, description, date, start, end, recurring);
        user.saveEventToCSV(event);
        System.out.println("Event saved for user " + user.getUsername() + " with event ID " + event.getId());

        // Quick read-back confirms user-specific filtering is working.
        List<Event> myEvents = user.loadMyEvents();
        System.out.println("You currently have " + myEvents.size() + " stored event(s).");
    }

    // Reads a date until input matches ISO format yyyy-MM-dd.
    private static LocalDate readDate(Scanner scanner) {
        while (true) {
            System.out.println("Event date (YYYY-MM-DD): ");
            String raw = scanner.nextLine().trim();

            try {
                return LocalDate.parse(raw);
            } catch (DateTimeParseException ex) {
                System.out.println("Invalid date format. Example: 2026-04-15");
            }
        }
    }

    // Reads a time until input matches ISO format HH:mm.
    private static LocalTime readTime(Scanner scanner, String prompt) {
        while (true) {
            System.out.println(prompt);
            String raw = scanner.nextLine().trim();

            try {
                return LocalTime.parse(raw);
            } catch (DateTimeParseException ex) {
                System.out.println("Invalid time format. Example: 14:30");
            }
        }
    }
}
