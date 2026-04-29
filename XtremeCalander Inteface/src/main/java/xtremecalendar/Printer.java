package xtremecalendar;

import java.util.List;

public class Printer {

    public boolean printDocument(List<String> formattedData) {
        if (formattedData == null || formattedData.isEmpty()) {
            System.out.println("No content to print.");
            return false;
        }

        System.out.println("=== PRINTING DOCUMENT ===");
        for (String line : formattedData) {
            System.out.println(line);
        }
        System.out.println("=== PRINT COMPLETE ===");
        return true;
    }
}
