package xtremecalendar;

import java.util.List;

public class PrintSequence {

    private final PrintService printService;
    private final Printer printer;
    private final Export exporter;

    public PrintSequence() {
        this.printService = new PrintService();
        this.printer = new Printer();
        this.exporter = new Export();
    }

    public void executePrint(List<Event> events) {
        System.out.println("User requested print...");

        List<String> formattedData = printService.formatEvents(events);
        boolean success = printer.printDocument(formattedData);

        if (success) {
            System.out.println("Print confirmed to user.");
        } else {
            System.out.println("Print failed.");
        }
    }

    public void executeExport(List<Event> events, String filePath) {
        System.out.println("User requested export...");

        List<String> formattedData = printService.formatEvents(events);
        boolean success = exporter.generateExportFile(formattedData, filePath);

        if (success) {
            System.out.println("Export confirmed to user.");
        } else {
            System.out.println("Export failed.");
        }
    }
}
