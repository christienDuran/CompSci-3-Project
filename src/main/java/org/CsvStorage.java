package org;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Handles CSV persistence for users, events, and reminders.
 *
 * Reminder storage is account-scoped so each logged-in user sees only their own reminders.
 * Reminder rows are rewritten when the UI saves, snoozes, dismisses, or reloads reminder state.
 */
public final class CsvStorage {
    // CSV file where account credentials are stored.
    private static final Path USERS_PATH = Paths.get("users.csv");
    // CSV file where event records are stored.
    private static final Path EVENTS_PATH = Paths.get("events.csv");
    // CSV file where reminder records are stored.
    private static final Path REMINDERS_PATH = Paths.get("reminders.csv");
    // CSV file where goal records are stored.
    private static final Path GOALS_PATH = Paths.get("goals.csv");
    // CSV file where the current signed-in account ID is stored.
    private static final Path SESSION_PATH = Paths.get("session.csv");
    // Header line for users.csv.
    private static final String USERS_HEADER = "account_id,username,password";
    // Header line for events.csv.
    private static final String EVENTS_HEADER = "account_id,event_id,title,description,date,start_time,end_time,recurring";
    // Header line for reminders.csv.
    private static final String REMINDERS_HEADER = "account_id,reminder_id,event_id,event_description,minutes_before,snooze_length,trigger_at,active,fired";
    // Header line for goals.csv.
    private static final String GOALS_HEADER = "account_id,goal_id,name,current_value,target_value";
    // Header line for session.csv.
    private static final String SESSION_HEADER = "account_id";

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

    // Finds and returns a user row by account ID.
    // Returns null if no matching user is found.
    public static synchronized UserAccount findUserByAccountId(int accountId) {
        // Used by CalendarFxApp startup to restore remembered sessions.
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

                int rowAccountId;
                try {
                    rowAccountId = Integer.parseInt(fields.get(0));
                } catch (NumberFormatException ex) {
                    continue;
                }

                if (rowAccountId == accountId) {
                    String storedUsername = fields.get(1);
                    String password = fields.get(2);
                    return new UserAccount(storedUsername, password, rowAccountId);
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

    // Deletes a single event row for the given account and event ID.
    public static synchronized void deleteEventForUser(int accountId, int eventId) {
        ensureEventsFile();

        List<String> rows = new ArrayList<>();
        rows.add(EVENTS_HEADER);

        try {
            List<String> lines = Files.readAllLines(EVENTS_PATH, StandardCharsets.UTF_8);
            for (int i = 1; i < lines.size(); i++) {
                String line = lines.get(i);
                if (line.isBlank()) {
                    continue;
                }

                List<String> fields = parseCsvLine(line);
                if (fields.size() < 8) {
                    // Keep malformed rows untouched to avoid data loss.
                    rows.add(line);
                    continue;
                }

                try {
                    int rowAccountId = Integer.parseInt(fields.get(0));
                    int rowEventId = Integer.parseInt(fields.get(1));

                    if (rowAccountId == accountId && rowEventId == eventId) {
                        continue;
                    }
                } catch (NumberFormatException ignored) {
                    // Keep malformed rows untouched.
                }

                rows.add(line);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed reading events CSV", e);
        }

        writeAllLines(EVENTS_PATH, rows);
    }

    // Rewrites one event row for the given account and event ID.
    public static synchronized void updateEventForUser(int accountId, Event updatedEvent) {
        ensureEventsFile();

        if (updatedEvent == null) {
            throw new IllegalArgumentException("updatedEvent is required.");
        }
        if (updatedEvent.getId() <= 0) {
            throw new IllegalArgumentException("Event ID is required for update.");
        }
        if (updatedEvent.getDate() == null || updatedEvent.getStartTime() == null || updatedEvent.getEndTime() == null) {
            throw new IllegalArgumentException("Event date/start/end time are required before updating.");
        }

        List<String> rows = new ArrayList<>();
        rows.add(EVENTS_HEADER);
        boolean updated = false;

        try {
            List<String> lines = Files.readAllLines(EVENTS_PATH, StandardCharsets.UTF_8);
            for (int i = 1; i < lines.size(); i++) {
                String line = lines.get(i);
                if (line.isBlank()) {
                    continue;
                }

                List<String> fields = parseCsvLine(line);
                if (fields.size() < 8) {
                    rows.add(line);
                    continue;
                }

                try {
                    int rowAccountId = Integer.parseInt(fields.get(0));
                    int rowEventId = Integer.parseInt(fields.get(1));

                    if (rowAccountId == accountId && rowEventId == updatedEvent.getId()) {
                        rows.add(accountId
                            + "," + updatedEvent.getId()
                            + "," + escape(updatedEvent.getTitle())
                            + "," + escape(updatedEvent.getDescription())
                            + "," + updatedEvent.getDate()
                            + "," + updatedEvent.getStartTime()
                            + "," + updatedEvent.getEndTime()
                            + "," + updatedEvent.isRecurring());
                        updated = true;
                        continue;
                    }
                } catch (NumberFormatException ignored) {
                    // Keep malformed rows untouched.
                }

                rows.add(line);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed reading events CSV", e);
        }

        if (!updated) {
            throw new IllegalArgumentException("Event #" + updatedEvent.getId() + " was not found for this user.");
        }

        writeAllLines(EVENTS_PATH, rows);
    }

    // Saves all reminders for one account by rewriting reminders.csv.
    public static synchronized void saveRemindersForUser(int accountId, List<Reminder> reminders) {
        ensureRemindersFile();

        // Rewrite the file so only this account's reminder rows are replaced.
        List<String> preservedRows = new ArrayList<>();
        preservedRows.add(REMINDERS_HEADER);

        List<ReminderRow> existingRows = loadReminderRows();
        for (ReminderRow row : existingRows) {
            if (row.accountId != accountId) {
                preservedRows.add(row.toCsvRow());
            }
        }

        for (Reminder reminder : reminders) {
            preservedRows.add(toReminderRow(accountId, reminder).toCsvRow());
        }

        writeAllLines(REMINDERS_PATH, preservedRows);
    }

    // Loads all reminders belonging to a specific account.
    public static synchronized List<Reminder> loadRemindersForUser(int accountId) {
        ensureRemindersFile();
        List<Reminder> reminders = new ArrayList<>();

        // Rehydrate reminder state from CSV so the UI can resume alerts after restart.
        for (ReminderRow row : loadReminderRows()) {
            if (row.accountId != accountId) {
                continue;
            }

            Reminder reminder = new Reminder(row.reminderId, row.eventId, row.eventDescription, row.minutesBefore, row.snoozeLength);
            reminder.setTriggerAt(row.triggerAt);
            reminder.setActive(row.active);
            reminder.setFired(row.fired);
            reminders.add(reminder);
        }

        return reminders;
    }

    // Saves all goals for one account by rewriting goals.csv for that account slice.
    public static synchronized void saveGoalsForUser(int accountId, List<Goal> goals) {
        ensureGoalsFile();

        List<String> rows = new ArrayList<>();
        rows.add(GOALS_HEADER);

        for (GoalRow row : loadGoalRows()) {
            if (row.accountId != accountId) {
                rows.add(row.toCsvRow());
            }
        }

        for (Goal goal : goals) {
            if (goal.getGoalId() <= 0) {
                goal.setGoalId(nextGoalId());
            }
            rows.add(new GoalRow(
                accountId,
                goal.getGoalId(),
                goal.getName(),
                goal.getCurrentValue(),
                goal.getTargetValue()
            ).toCsvRow());
        }

        writeAllLines(GOALS_PATH, rows);
    }

    // Loads all goals belonging to a specific account.
    public static synchronized List<Goal> loadGoalsForUser(int accountId) {
        ensureGoalsFile();
        List<Goal> goals = new ArrayList<>();

        for (GoalRow row : loadGoalRows()) {
            if (row.accountId != accountId) {
                continue;
            }
            goals.add(new Goal(row.goalId, row.name, row.currentValue, row.targetValue));
        }

        return goals;
    }

    // Persists the currently signed-in account ID.
    public static synchronized void saveCurrentSession(int accountId) {
        // Keep only one active remembered session row.
        ensureSessionFile();
        List<String> rows = new ArrayList<>();
        rows.add(SESSION_HEADER);
        rows.add(String.valueOf(accountId));
        writeAllLines(SESSION_PATH, rows);
    }

    // Loads the currently signed-in account ID, or null when no session exists.
    public static synchronized Integer loadCurrentSessionAccountId() {
        // CalendarFxApp treats null as "show login screen".
        ensureSessionFile();

        try {
            List<String> lines = Files.readAllLines(SESSION_PATH, StandardCharsets.UTF_8);
            if (lines.size() < 2 || lines.get(1).isBlank()) {
                return null;
            }

            List<String> fields = parseCsvLine(lines.get(1));
            String raw = fields.isEmpty() ? "" : fields.get(0);
            if (raw.isBlank()) {
                return null;
            }

            return Integer.parseInt(raw.trim());
        } catch (IOException e) {
            throw new RuntimeException("Failed reading session CSV", e);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // Clears any persisted signed-in session.
    public static synchronized void clearCurrentSession() {
        // Called on explicit logout and when remembered user no longer exists.
        ensureSessionFile();
        writeHeader(SESSION_PATH, SESSION_HEADER);
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

    // Computes next goal ID globally across goals.csv.
    private static synchronized int nextGoalId() {
        ensureGoalsFile();
        int max = 0;

        try {
            List<String> lines = Files.readAllLines(GOALS_PATH, StandardCharsets.UTF_8);
            for (int i = 1; i < lines.size(); i++) {
                String line = lines.get(i);
                if (line.isBlank()) {
                    continue;
                }

                List<String> fields = parseCsvLine(line);
                if (fields.size() > 1) {
                    try {
                        int goalId = Integer.parseInt(fields.get(1));
                        max = Math.max(max, goalId);
                    } catch (NumberFormatException ignored) {
                        // Ignore malformed rows.
                    }
                }
            }
            return max + 1;
        } catch (IOException e) {
            throw new RuntimeException("Failed reading goals CSV", e);
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

    // Creates reminders.csv with header if it does not exist.
    private static void ensureRemindersFile() {
        if (Files.exists(REMINDERS_PATH)) {
            return;
        }
        writeHeader(REMINDERS_PATH, REMINDERS_HEADER);
    }

    // Creates goals.csv with header if it does not exist.
    private static void ensureGoalsFile() {
        if (Files.exists(GOALS_PATH)) {
            return;
        }
        writeHeader(GOALS_PATH, GOALS_HEADER);
    }

    // Creates session.csv with header if it does not exist.
    private static void ensureSessionFile() {
        if (Files.exists(SESSION_PATH)) {
            return;
        }
        writeHeader(SESSION_PATH, SESSION_HEADER);
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

    // Writes a complete file with the provided lines.
    private static void writeAllLines(Path path, List<String> lines) {
        try {
            Files.write(path, String.join(System.lineSeparator(), lines).concat(System.lineSeparator()).getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
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

    private static ReminderRow toReminderRow(int accountId, Reminder reminder) {
        // Convert the in-memory reminder into one CSV row snapshot.
        return new ReminderRow(
            accountId,
            reminder.getReminderId(),
            reminder.getEventId(),
            reminder.getEventDescription(),
            reminder.getMinutesBefore(),
            reminder.getSnoozeLength(),
            reminder.getTriggerAt(),
            reminder.isActive(),
            reminder.isFired()
        );
    }

    private static List<ReminderRow> loadReminderRows() {
        List<ReminderRow> rows = new ArrayList<>();

        try {
            List<String> lines = Files.readAllLines(REMINDERS_PATH, StandardCharsets.UTF_8);
            for (int i = 1; i < lines.size(); i++) {
                String line = lines.get(i);
                if (line.isBlank()) {
                    continue;
                }

                List<String> fields = parseCsvLine(line);
                if (fields.size() < 9) {
                    continue;
                }

                try {
                    int accountId = Integer.parseInt(fields.get(0));
                    int reminderId = Integer.parseInt(fields.get(1));
                    int eventId = Integer.parseInt(fields.get(2));
                    String eventDescription = fields.get(3);
                    int minutesBefore = Integer.parseInt(fields.get(4));
                    int snoozeLength = Integer.parseInt(fields.get(5));
                    LocalDateTime triggerAt = fields.get(6).isBlank() ? null : LocalDateTime.parse(fields.get(6));
                    boolean active = Boolean.parseBoolean(fields.get(7));
                    boolean fired = Boolean.parseBoolean(fields.get(8));

                    rows.add(new ReminderRow(accountId, reminderId, eventId, eventDescription, minutesBefore, snoozeLength, triggerAt, active, fired));
                } catch (RuntimeException ignored) {
                    // Ignore malformed rows.
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed reading reminders CSV", e);
        }

        return rows;
    }

    private static List<GoalRow> loadGoalRows() {
        List<GoalRow> rows = new ArrayList<>();

        try {
            List<String> lines = Files.readAllLines(GOALS_PATH, StandardCharsets.UTF_8);
            for (int i = 1; i < lines.size(); i++) {
                String line = lines.get(i);
                if (line.isBlank()) {
                    continue;
                }

                List<String> fields = parseCsvLine(line);
                if (fields.size() < 5) {
                    continue;
                }

                try {
                    int accountId = Integer.parseInt(fields.get(0));
                    int goalId = Integer.parseInt(fields.get(1));
                    String name = fields.get(2);
                    double currentValue = Double.parseDouble(fields.get(3));
                    double targetValue = Double.parseDouble(fields.get(4));
                    rows.add(new GoalRow(accountId, goalId, name, currentValue, targetValue));
                } catch (RuntimeException ignored) {
                    // Ignore malformed rows.
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed reading goals CSV", e);
        }

        return rows;
    }

    private static final class ReminderRow {
        private final int accountId;
        private final int reminderId;
        private final int eventId;
        private final String eventDescription;
        private final int minutesBefore;
        private final int snoozeLength;
        private final LocalDateTime triggerAt;
        private final boolean active;
        private final boolean fired;

        private ReminderRow(int accountId, int reminderId, int eventId, String eventDescription, int minutesBefore,
                            int snoozeLength, LocalDateTime triggerAt, boolean active, boolean fired) {
            this.accountId = accountId;
            this.reminderId = reminderId;
            this.eventId = eventId;
            this.eventDescription = eventDescription == null ? "" : eventDescription;
            this.minutesBefore = minutesBefore;
            this.snoozeLength = snoozeLength;
            this.triggerAt = triggerAt;
            this.active = active;
            this.fired = fired;
        }

        private String toCsvRow() {
            // Keep the CSV row format aligned with REMINDERS_HEADER.
            return accountId + ","
                + reminderId + ","
                + eventId + ","
                + escape(eventDescription) + ","
                + minutesBefore + ","
                + snoozeLength + ","
                + (triggerAt == null ? "" : triggerAt) + ","
                + active + ","
                + fired;
        }
    }

    private static final class GoalRow {
        private final int accountId;
        private final int goalId;
        private final String name;
        private final double currentValue;
        private final double targetValue;

        private GoalRow(int accountId, int goalId, String name, double currentValue, double targetValue) {
            this.accountId = accountId;
            this.goalId = goalId;
            this.name = name == null ? "" : name;
            this.currentValue = currentValue;
            this.targetValue = targetValue;
        }

        private String toCsvRow() {
            return accountId + ","
                + goalId + ","
                + escape(name) + ","
                + currentValue + ","
                + targetValue;
        }
    }
}
