package org;

import java.time.LocalDate;
import java.time.LocalDateTime;
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
        return createEvent(
            user,
            title,
            description,
            dateRaw,
            startRaw,
            endRaw,
            recurring ? Event.RECURRENCE_WEEKLY : Event.RECURRENCE_NONE
        );
    }

    public static Event createEvent(UserAccount user,
                                    String title,
                                    String description,
                                    String dateRaw,
                                    String startRaw,
                                    String endRaw,
                                    String recurrencePattern) {
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

        Event event = new Event(0, normalizedTitle, description == null ? "" : description.trim(), date, start, end, recurrencePattern);
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

        System.out.println("Recurrence (NONE/DAILY/WEEKLY/MONTHLY): ");
        String recurrencePattern = normalizeRecurrencePattern(userInput.nextLine());

        Event event = createEvent(
            user,
            title,
            description,
            date.toString(),
            start.toString(),
            end.toString(),
            recurrencePattern
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

    // Recurring events repeat weekly on the same day-of-week starting from the base date.
    public static boolean occursOn(Event event, LocalDate candidateDate) {
        if (event == null || candidateDate == null || event.getDate() == null) {
            return false;
        }

        LocalDate baseDate = event.getDate();
        if (candidateDate.equals(baseDate)) {
            return true;
        }

        if (candidateDate.isBefore(baseDate)) {
            return false;
        }

        String recurrence = normalizeRecurrencePattern(event.getRecurrencePattern());
        return switch (recurrence) {
            case Event.RECURRENCE_DAILY -> true;
            case Event.RECURRENCE_WEEKLY -> candidateDate.getDayOfWeek() == baseDate.getDayOfWeek();
            case Event.RECURRENCE_MONTHLY -> {
                int expectedDay = Math.min(baseDate.getDayOfMonth(), candidateDate.lengthOfMonth());
                yield candidateDate.getDayOfMonth() == expectedDay;
            }
            default -> false;
        };
    }

    // Lightweight occurrence copy used by calendar/day popups without mutating persisted events.
    public static Event asOccurrence(Event source, LocalDate occurrenceDate) {
        if (source == null || occurrenceDate == null) {
            throw new IllegalArgumentException("Source event and occurrence date are required.");
        }

        return new Event(
            source.getId(),
            source.getTitle(),
            source.getDescription(),
            occurrenceDate,
            source.getStartTime(),
            source.getEndTime(),
            source.getRecurrencePattern()
        );
    }

    public static LocalDateTime nextOccurrenceStartOnOrAfter(Event event, LocalDateTime threshold) {
        if (event == null || threshold == null || event.getDate() == null || event.getStartTime() == null) {
            return null;
        }

        LocalDate baseDate = event.getDate();
        LocalTime startTime = event.getStartTime();
        LocalDate candidateDate = threshold.toLocalDate();
        LocalDateTime baseStart = LocalDateTime.of(baseDate, startTime);

        String recurrence = normalizeRecurrencePattern(event.getRecurrencePattern());
        if (Event.RECURRENCE_NONE.equals(recurrence)) {
            return baseStart.isBefore(threshold) ? null : baseStart;
        }

        if (candidateDate.isBefore(baseDate)) {
            candidateDate = baseDate;
        }

        if (Event.RECURRENCE_DAILY.equals(recurrence)) {
            LocalDateTime candidateStart = LocalDateTime.of(candidateDate, startTime);
            if (candidateStart.isBefore(threshold)) {
                candidateStart = candidateStart.plusDays(1);
            }
            return candidateStart;
        }

        if (Event.RECURRENCE_WEEKLY.equals(recurrence)) {
            int offset = (baseDate.getDayOfWeek().getValue() - candidateDate.getDayOfWeek().getValue() + 7) % 7;
            LocalDate weeklyDate = candidateDate.plusDays(offset);
            LocalDateTime weeklyStart = LocalDateTime.of(weeklyDate, startTime);
            if (weeklyStart.isBefore(threshold)) {
                weeklyStart = weeklyStart.plusWeeks(1);
            }
            return weeklyStart;
        }

        // Monthly recurrence keeps the base day-of-month when possible, otherwise uses month end.
        LocalDate monthCursor = LocalDate.of(candidateDate.getYear(), candidateDate.getMonth(), 1);
        if (monthCursor.isBefore(LocalDate.of(baseDate.getYear(), baseDate.getMonth(), 1))) {
            monthCursor = LocalDate.of(baseDate.getYear(), baseDate.getMonth(), 1);
        }

        while (true) {
            int day = Math.min(baseDate.getDayOfMonth(), monthCursor.lengthOfMonth());
            LocalDate occurrenceDate = monthCursor.withDayOfMonth(day);
            if (!occurrenceDate.isBefore(baseDate)) {
                LocalDateTime occurrenceStart = LocalDateTime.of(occurrenceDate, startTime);
                if (!occurrenceStart.isBefore(threshold)) {
                    return occurrenceStart;
                }
            }
            monthCursor = monthCursor.plusMonths(1).withDayOfMonth(1);
        }
    }

    public static String normalizeRecurrencePattern(String recurrencePattern) {
        String normalized = recurrencePattern == null ? "" : recurrencePattern.trim().toUpperCase(Locale.ENGLISH);
        return switch (normalized) {
            case Event.RECURRENCE_DAILY, Event.RECURRENCE_WEEKLY, Event.RECURRENCE_MONTHLY -> normalized;
            default -> Event.RECURRENCE_NONE;
        };
    }
}
