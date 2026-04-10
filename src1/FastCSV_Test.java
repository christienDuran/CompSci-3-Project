





import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;

import de.siegmar.fastcsv.writer.CsvWriter; // need import in order to write into a csv file
import de.siegmar.fastcsv.reader.CsvReader;  // need import in order to read into a csv file

import java.util.Scanner;

// This is what I made when testing the FastCSV library
// if you want to test this, you will need to copy and past this data, put this library
// dependency -> implementation 'de.siegmar:fastcsv:4.2.0' , inside a project's build.gradle and run it


public class FastCSV_Test {
    public static void main(String[] args) throws IOException {


        System.out.println(" Hello, can you please enter your username: ");
        Scanner userInput = new Scanner(System.in);

        String username = userInput.nextLine();
        System.out.println(" Now enter your password: ");
        String password = userInput.nextLine();



        // writes to a csv file -> if it does not exist, it creates it, then writes to the file

        CsvWriter CsvWriterBuilder = null;
        try (FileWriter fileWriter = new FileWriter("output.csv");
             CsvWriter csvWriter = CsvWriterBuilder.builder().build(fileWriter)) {

            csvWriter.writeRecord(username, password, "City");

        }

        // This reads from the csv file and outputs the contents to the terminal, but won't be organized
//        try (FileReader fileReader = new FileReader("output.csv")) {
//
//            CsvReader.builder()
//                    .ofCsvRecord(Path.of("output.csv"))
//                    .forEach(record -> System.out.println(record));
//
//
//        }





        // This format outputs the contents of the csv file to the terminal within a clean manner
        CsvReader.builder()
                .ofCsvRecord(Path.of("output.csv"))
                .forEach(record -> {
                    System.out.println(
                            record.getField(0) + ", " +
                                    record.getField(1) + ", "
                            // record.getField(2)
                    );
                });

    }
}
