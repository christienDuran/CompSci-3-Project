package xtremecalendar;
import java.time.LocalDate;

public class CalendarEvent {

    private final LocalDate date;
    private final String title;

    public CalendarEvent(LocalDate date, String title) {
        this.date = date;
        this.title = title;
    }
    public LocalDate getDate() {
        return date;
    }
    public String getTitle() {
        return title;
    }
}
