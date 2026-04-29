package xtremecalendar;

import java.util.ArrayList;
import java.util.List;

public class PrintService {

    public List<String> formatEvents(List<Event> events) {
        List<String> formatted = new ArrayList<>();

        for (Event event : events) {
            formatted.add(event.toDisplayString());
        }

        return formatted;
    }
}
