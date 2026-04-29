package xtremecalendar;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class DemoCalendarBackend implements CalendarBackend {

    @Override
    public List<CalendarEvent> getEvents() {
        List<CalendarEvent> events = new ArrayList<>();
        LocalDate today = LocalDate.now();
        events.add(new CalendarEvent(today, "Sprint Planning"));
        events.add(new CalendarEvent(today.plusDays(1), "Backend Sync"));
        events.add(new CalendarEvent(today.plusDays(3), "Export Demo"));
        return events;
    }
    @Override
    public List<CalendarEvent> getEventsByRange(LocalDate start, LocalDate end) {
        List<CalendarEvent> matchingEvents = new ArrayList<>();
        for (CalendarEvent event : getEvents()) {
            LocalDate date = event.getDate();
            if ((date.isEqual(start) || date.isAfter(start))
                    && (date.isEqual(end) || date.isBefore(end))) {
                matchingEvents.add(event);
            }
        }
        return matchingEvents;
    }
}
