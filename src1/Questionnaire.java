package org.example

import java.sql.SQLOutput;
import java.util.Scanner;
import java.util.ArrayList;

public class Questionnaire {
    private Scanner userInput = new Scanner(System.in);

    //Ask Yes/No question
    public  boolean askRecurring(){
        System.out.println("Do you want recurring events (Y/N) ");
        String answer = userInput.nextLine();

        //the .equalsIgnoreCase makes the system not care if input is lowercase or upper. Anything else will be false
        if(answer.equalsIgnoreCase("Y")){
            return true;
        }else {
            return false;
        }
    }

    public Event createEvent(){
        System.out.println(" Enter event details: ");

        System.out.println("\n Title :");
        String title = userInput.nextLine();

        System.out.println("\n Day: ");
        String day = userInput.nextLine();

        System.out.println("\n Set Time: ");  // will be implemented by something else
        String time = userInput.nextLine();

        System.out.println("\n Recurrence daily/weekly/monthly:  ");
        String recurrence = userInput.nextLine();

        return new Event(title, day, time, recurrence);


    }

    public ArrayList<Event> run(){ // run is a method name "ArrayList<Event>" , return a list of Event objects
        ArraList<Event>recurrEvents = new ArrayList<Event>(); // prepares an empty container to store recurring events

        if(askRecurring()){
            Event event = createEvent(); // creates a new event from gathering (Title, Day, Time, when event recurs
            recurrEvents.add(event);  // adds to the empty Questionnaire list, that will add to out "main" event class,
        }else {                         // which will then be displayed to the user's calendar
            System.out.println(" Skipped Questionnaire");
        }
            return recurrEvents;  // return the user to

    }
}