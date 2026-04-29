package xtremecalendar;

import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

public class Export {

    public boolean generateExportFile(List<String> data, String filePath) {
        if (data == null || data.isEmpty()) {
            System.out.println("Export Error: No data to export.");
            return false;
        }

        try (FileWriter writer = new FileWriter(filePath)) {
            for (String line : data) {
                writer.write(line + System.lineSeparator());
            }

            System.out.println("Export successful: " + filePath);
            return true;
        } catch (IOException exception) {
            System.out.println("Export failed: " + exception.getMessage());
            return false;
        }
    }
}
