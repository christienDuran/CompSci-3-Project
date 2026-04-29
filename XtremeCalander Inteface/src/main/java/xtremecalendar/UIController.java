package xtremecalendar;

import javafx.concurrent.Worker;
import javafx.fxml.FXML;
import javafx.print.PrinterJob;
import javafx.scene.Scene;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import netscape.javascript.JSObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class UIController {

    @FXML
    private WebView webView;

    private WebEngine engine;
    private WebEngine popupEngine;
    private Stage popupStage;
    private final CalendarBackend backend = new DemoCalendarBackend();
    private final PrintSequence printSequence = new PrintSequence();

    @FXML
    public void initialize() {
        engine = webView.getEngine();
        engine.setCreatePopupHandler(config -> createPopupEngine());
        engine.getLoadWorker().stateProperty().addListener((observable, oldState, newState) -> {
            if (newState == Worker.State.SUCCEEDED) {
                JSObject window = (JSObject) engine.executeScript("window");
                window.setMember("javaController", this);
                refreshCalendar();
            }
        });

        engine.load(
                Objects.requireNonNull(
                        getClass().getResource("/xtremecalendar/calendar.html"),
                        "Could not find /xtremecalendar/calendar.html"
                ).toExternalForm()
        );
    }

    @FXML
    public void showCalendar() {
        refreshCalendar();
        engine.executeScript("showCalendar()");
    }

    @FXML
    public void handleExport() {
        executeExportSequence();
    }

    @FXML
    public void handlePrint() {
        executePrintSequence();
    }

    public void handlePrintFromJS() {
        executePrintSequence();
    }

    public void handleExportFromJS() {
        executeExportSequence();
    }

    public void getEventsForDay(int day) {
        System.out.println("Fetching events for day: " + day);

        List<String> titles = backend.getEvents().stream()
                .filter(event -> event.getDate().getDayOfMonth() == day)
                .map(CalendarEvent::getTitle)
                .collect(Collectors.toList());

        if (titles.isEmpty()) {
            titles.add("No events");
        }

        engine.executeScript("showEvents(" + toJsArray(titles) + ")");
        engine.executeScript("updateProgress(" + calculateProgress(day) + ")");
        engine.executeScript("setStatus('Loaded details for day " + day + "')");
    }

    public void handleDateSelected(String isoDate) {
        engine.executeScript("setStatus('Selected " + escapeJs(isoDate) + "')");
        System.out.println("Date selected in WebView: " + isoDate);
    }

    public void printPreview() {
        if (popupEngine == null || popupStage == null) {
            engine.executeScript("setStatus('Open print preview before printing')");
            return;
        }

        PrinterJob job = PrinterJob.createPrinterJob();
        if (job == null) {
            engine.executeScript("setStatus('No printer is available')");
            return;
        }

        boolean approved = job.showPrintDialog(popupStage);
        if (!approved) {
            engine.executeScript("setStatus('Print canceled')");
            return;
        }

        popupEngine.print(job);
        boolean success = job.endJob();
        if (success) {
            engine.executeScript("setStatus('Print job sent')");
        } else {
            engine.executeScript("setStatus('Print job failed')");
        }
    }

    private void executePrintSequence() {
        printSequence.executePrint(toPlaceholderEvents(backend.getEvents()));
        engine.executeScript("setStatus('Print sequence executed')");
    }

    private void executeExportSequence() {
        String exportPath = buildExportPath();
        ensureExportDirectory(exportPath);
        printSequence.executeExport(toPlaceholderEvents(backend.getEvents()), exportPath);

        if (Files.exists(Path.of(exportPath))) {
            engine.executeScript("setStatus('Exported events to " + escapeJs(exportPath) + "')");
        } else {
            engine.executeScript("setStatus('Export sequence failed')");
        }
    }

    private void refreshCalendar() {
        engine.executeScript("clearEvents()");
        List<CalendarEvent> events = backend.getEvents();
        for (CalendarEvent event : events) {
            String script = String.format(
                    "addEvent(%d, '%s')",
                    event.getDate().getDayOfMonth(),
                    escapeJs(event.getTitle())
            );
            engine.executeScript(script);
        }

        LocalDate today = LocalDate.now();
        List<CalendarEvent> weekEvents = backend.getEventsByRange(today, today.plusDays(6));
        engine.executeScript("setStatus('Loaded " + weekEvents.size() + " event(s) for the next 7 days')");
    }

    private String escapeJs(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\r", "")
                .replace("\n", "\\n");
    }

    private String toJsArray(List<String> values) {
        return values.stream()
                .map(value -> "'" + escapeJs(value) + "'")
                .collect(Collectors.joining(", ", "[", "]"));
    }

    private int calculateProgress(int day) {
        int matchingEvents = 0;
        for (CalendarEvent event : backend.getEvents()) {
            if (event.getDate().getDayOfMonth() == day) {
                matchingEvents++;
            }
        }

        return Math.min(100, matchingEvents * 40);
    }

    private List<Event> toPlaceholderEvents(List<CalendarEvent> calendarEvents) {
        return calendarEvents.stream()
                .map(this::toPlaceholderEvent)
                .collect(Collectors.toList());
    }

    private Event toPlaceholderEvent(CalendarEvent calendarEvent) {
        return new Event(
                calendarEvent.getTitle(),
                calendarEvent.getDate().toString(),
                "TBD",
                "Placeholder details until the event module is connected"
        );
    }

    private String buildExportPath() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        return Path.of("exports", "events-" + timestamp + ".csv").toString();
    }

    private void ensureExportDirectory(String exportPath) {
        Path parent = Path.of(exportPath).getParent();
        if (parent == null) {
            return;
        }

        try {
            Files.createDirectories(parent);
        } catch (IOException exception) {
            System.out.println("Could not prepare export directory: " + exception.getMessage());
        }
    }

    private WebEngine createPopupEngine() {
        WebView popupView = new WebView();
        popupEngine = popupView.getEngine();
        popupEngine.getLoadWorker().stateProperty().addListener((observable, oldState, newState) -> {
            if (newState == Worker.State.SUCCEEDED) {
                JSObject window = (JSObject) popupEngine.executeScript("window");
                window.setMember("javaController", this);
            }
        });

        popupStage = new Stage();
        popupStage.setTitle("Print Preview");
        popupStage.setScene(new Scene(popupView, 900, 700));
        popupStage.setOnHidden(event -> {
            popupEngine = null;
            popupStage = null;
        });
        popupStage.show();

        return popupEngine;
    }
}
