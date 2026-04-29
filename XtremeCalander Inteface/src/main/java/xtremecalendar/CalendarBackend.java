package xtremecalendar;

import java.time.LocalDate;
import java.util.List;

public interface CalendarBackend {

    List<CalendarEvent> getEvents();

    List<CalendarEvent> getEventsByRange(LocalDate start, LocalDate end);
}
