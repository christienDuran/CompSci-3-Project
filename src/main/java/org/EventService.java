package org;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;

/**
 * Handles event creation prompts, validation, and persistence.
 */
public final class EventService {
    private static final DateTimeFormatter TIME_12_HOUR = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);

    private EventService() {
    }

    // UI/Service entry point for event creation.
    public static Event createEvent(UserAccount user,
                                    String title,
                                    String description,
                                    String dateRaw,
                                    String startRaw,
                                    String endRaw,
                                    boolean recurring) {
        if (user == null) {
            throw new IllegalArgumentException("User session is required.");
        }

        String normalizedTitle = title == null ? "" : title.trim();
        if (normalizedTitle.isEmpty()) {
            throw new IllegalArgumentException("Event title is required.");
        }

        LocalDate date;
        LocalTime start;
        LocalTime end;

        try {
            date = LocalDate.parse(dateRaw.trim());
        } catch (Exception ex) {
            throw new IllegalArgumentException("Date must be YYYY-MM-DD.");
        }

        try {
            start = parseTime(startRaw);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Start time must be like 9:00 AM.");
        }

        try {
            end = parseTime(endRaw);
        } catch (Exception ex) {
            throw new IllegalArgumentException("End time must be like 10:30 AM.");
        }

        if (end.isBefore(start)) {
            throw new IllegalArgumentException("End time cannot be before start time.");
        }

        Event event = new Event(0, normalizedTitle, description == null ? "" : description.trim(), date, start, end, recurring);
        user.saveEventToCSV(event);
        return event;
    }

    // Event creation flow:
    // 1) gather event fields, 2) validate times, 3) save event for the current account.
    public static void createEventFlow(Scanner userInput, UserAccount user) {
        System.out.println("Event title: ");
        String title = userInput.nextLine();

        System.out.println("Event description: ");
        String description = userInput.nextLine();

        LocalDate date = readDate(userInput);
        LocalTime start = readTime(userInput, "Start time (h:mm AM/PM): ");
        LocalTime end = readTime(userInput, "End time (h:mm AM/PM): ");

        while (end.isBefore(start)) {
            System.out.println("End time cannot be before start time. Please enter end time again.");
            end = readTime(userInput, "End time (h:mm AM/PM): ");
        }

        System.out.println("Is this recurring? (true/false): ");
        boolean recurring = Boolean.parseBoolean(userInput.nextLine().trim());

        Event event = createEvent(
            user,
            title,
            description,
            date.toString(),
            start.toString(),
            end.toString(),
            recurring
        );
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

    // Reads a time until input matches either h:mm AM/PM or HH:mm.
    private static LocalTime readTime(Scanner scanner, String prompt) {
        while (true) {
            System.out.println(prompt);
            String raw = scanner.nextLine().trim();

            try {
                return parseTime(raw);
            } catch (DateTimeParseException ex) {
                System.out.println("Invalid time format. Example: 9:00 AM");
            }
        }
    }

    private static LocalTime parseTime(String raw) {
        String normalized = raw == null ? "" : raw.trim();

        try {
            // Backward-compatible support for existing 24-hour values.
            return LocalTime.parse(normalized);
        } catch (DateTimeParseException ignored) {
            return LocalTime.parse(normalized.toUpperCase(Locale.ENGLISH), TIME_12_HOUR);
        }
    }
}
