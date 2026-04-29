package xtremecalendar;

public class Event {

    private final String title;
    private final String date;
    private final String time;
    private final String description;

    public Event(String title, String date, String time, String description) {
        this.title = title;
        this.date = date;
        this.time = time;
        this.description = description;
    }

    public String getTitle() {
        return title;
    }

    public String getDate() {
        return date;
    }

    public String getTime() {
        return time;
    }

    public String getDescription() {
        return description;
    }

    public String toDisplayString() {
        return title + " | " + date + " | " + time + " | " + description;
    }
}
