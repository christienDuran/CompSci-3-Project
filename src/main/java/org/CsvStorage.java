package org;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Handles CSV persistence for users and events.
 * Events are linked to users by accountId.
 */
public final class CsvStorage {
    // CSV file where account credentials are stored.
    private static final Path USERS_PATH = Paths.get("users.csv");
    // CSV file where event records are stored.
    private static final Path EVENTS_PATH = Paths.get("events.csv");
    // Header line for users.csv.
    private static final String USERS_HEADER = "account_id,username,password";
    // Header line for events.csv.
    private static final String EVENTS_HEADER = "account_id,event_id,title,description,date,start_time,end_time,recurring";

    private CsvStorage() {
    }

    // Saves one user row to users.csv.
    // Throws if username already exists to prevent duplicate accounts.
    public static synchronized void saveUser(UserAccount user) {
        ensureUsersFile();

        if (usernameExists(user.getUsername())) {
            throw new IllegalArgumentException("Username already exists: " + user.getUsername());
        }

        String row = user.getAccountID()
            + "," + escape(user.getUsername())
            + "," + escape(user.getPassword());

        appendLine(USERS_PATH, row);
    }

    // Checks users.csv for an existing username (case-insensitive).
    public static synchronized boolean usernameExists(String username) {
        ensureUsersFile();

        try {
            List<String> lines = Files.readAllLines(USERS_PATH, StandardCharsets.UTF_8);
            for (int i = 1; i < lines.size(); i++) {
                String line = lines.get(i);
                if (line.isBlank()) {
                    continue;
                }

                List<String> fields = parseCsvLine(line);
                if (fields.size() >= 2 && fields.get(1).equalsIgnoreCase(username)) {
                    return true;
                }
            }
            return false;
        } catch (IOException e) {
            throw new RuntimeException("Failed reading users CSV", e);
        }
    }

    // Finds and returns a user row by username (case-insensitive).
    // Returns null if no matching user is found.
    public static synchronized UserAccount findUserByUsername(String username) {
        ensureUsersFile();

        try {
            List<String> lines = Files.readAllLines(USERS_PATH, StandardCharsets.UTF_8);
            for (int i = 1; i < lines.size(); i++) {
                String line = lines.get(i);
                if (line.isBlank()) {
                    continue;
                }

                List<String> fields = parseCsvLine(line);
                if (fields.size() < 3) {
                    continue;
                }

                if (fields.get(1).equalsIgnoreCase(username)) {
                    int accountId;
                    try {
                        accountId = Integer.parseInt(fields.get(0));
                    } catch (NumberFormatException ex) {
                        return null;
                    }

                    String storedUsername = fields.get(1);
                    String password = fields.get(2);
                    return new UserAccount(storedUsername, password, accountId);
                }
            }
            return null;
        } catch (IOException e) {
            throw new RuntimeException("Failed reading users CSV", e);
        }
    }

    // Computes next account ID by scanning existing rows and taking max + 1.
    public static synchronized int nextAccountId() {
        ensureUsersFile();
        int max = 9999;

        try {
            List<String> lines = Files.readAllLines(USERS_PATH, StandardCharsets.UTF_8);
            for (int i = 1; i < lines.size(); i++) {
                String line = lines.get(i);
                if (line.isBlank()) {
                    continue;
                }

                List<String> fields = parseCsvLine(line);
                if (!fields.isEmpty()) {
                    try {
                        int accountId = Integer.parseInt(fields.get(0));
                        max = Math.max(max, accountId);
                    } catch (NumberFormatException ignored) {
                        // Ignore malformed rows.
                    }
                }
            }
            return max + 1;
        } catch (IOException e) {
            throw new RuntimeException("Failed reading users CSV", e);
        }
    }

    // Appends an event row for a specific account.
    // Auto-generates event ID if the provided event has ID <= 0.
    public static synchronized void saveEventForUser(int accountId, Event event) {
        ensureEventsFile();

        if (event.getDate() == null || event.getStartTime() == null || event.getEndTime() == null) {
            throw new IllegalArgumentException("Event date/start/end time are required before saving.");
        }

        if (event.getId() <= 0) {
            event.setId(nextEventId());
        }

        String row = accountId
            + "," + event.getId()
            + "," + escape(event.getTitle())
            + "," + escape(event.getDescription())
            + "," + event.getDate()
            + "," + event.getStartTime()
            + "," + event.getEndTime()
            + "," + event.isRecurring();

        appendLine(EVENTS_PATH, row);
    }

    // Loads only events whose account_id matches the provided account.
    public static synchronized List<Event> loadEventsForUser(int accountId) {
        ensureEventsFile();
        List<Event> events = new ArrayList<>();

        try {
            List<String> lines = Files.readAllLines(EVENTS_PATH, StandardCharsets.UTF_8);
            for (int i = 1; i < lines.size(); i++) {
                String line = lines.get(i);
                if (line.isBlank()) {
                    continue;
                }

                List<String> fields = parseCsvLine(line);
                if (fields.size() < 8) {
                    continue;
                }

                int rowAccountId;
                try {
                    rowAccountId = Integer.parseInt(fields.get(0));
                } catch (NumberFormatException ex) {
                    continue;
                }

                if (rowAccountId != accountId) {
                    continue;
                }

                int eventId = Integer.parseInt(fields.get(1));
                String title = fields.get(2);
                String description = fields.get(3);
                LocalDate date = LocalDate.parse(fields.get(4));
                LocalTime start = LocalTime.parse(fields.get(5));
                LocalTime end = LocalTime.parse(fields.get(6));
                boolean recurring = Boolean.parseBoolean(fields.get(7));

                events.add(new Event(eventId, title, description, date, start, end, recurring));
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed reading events CSV", e);
        }

        return events;
    }

    // Computes next event ID globally across events.csv.
    private static synchronized int nextEventId() {
        ensureEventsFile();
        int max = 0;

        try {
            List<String> lines = Files.readAllLines(EVENTS_PATH, StandardCharsets.UTF_8);
            for (int i = 1; i < lines.size(); i++) {
                String line = lines.get(i);
                if (line.isBlank()) {
                    continue;
                }

                List<String> fields = parseCsvLine(line);
                if (fields.size() > 1) {
                    try {
                        int eventId = Integer.parseInt(fields.get(1));
                        max = Math.max(max, eventId);
                    } catch (NumberFormatException ignored) {
                        // Ignore malformed rows.
                    }
                }
            }
            return max + 1;
        } catch (IOException e) {
            throw new RuntimeException("Failed reading events CSV", e);
        }
    }

    // Creates users.csv with header if it does not exist.
    private static void ensureUsersFile() {
        if (Files.exists(USERS_PATH)) {
            return;
        }
        writeHeader(USERS_PATH, USERS_HEADER);
    }

    // Creates events.csv with header if it does not exist.
    private static void ensureEventsFile() {
        if (Files.exists(EVENTS_PATH)) {
            return;
        }
        writeHeader(EVENTS_PATH, EVENTS_HEADER);
    }

    // Writes a header line to a CSV file, recreating it if needed.
    private static void writeHeader(Path path, String header) {
        try {
            Files.write(path, (header + System.lineSeparator()).getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("Failed creating CSV file: " + path, e);
        }
    }

    // Appends one fully formatted line to the target CSV file.
    private static void appendLine(Path path, String line) {
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8,
            StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            writer.write(line);
            writer.newLine();
        } catch (IOException e) {
            throw new RuntimeException("Failed writing CSV file: " + path, e);
        }
    }

    // Escapes commas/quotes/newlines according to CSV quoting rules.
    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        boolean needsQuotes = value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r");
        String escaped = value.replace("\"", "\"\"");
        return needsQuotes ? "\"" + escaped + "\"" : escaped;
    }

    // Parses one CSV line while handling quoted commas and escaped quotes.
    private static List<String> parseCsvLine(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                out.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }

        out.add(current.toString());
        return out;
    }
}
