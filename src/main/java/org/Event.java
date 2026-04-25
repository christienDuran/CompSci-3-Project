package org;

import java.time.LocalDate;
import java.time.LocalTime;

/*
Template for storing data for a calendar event. Generic superclass for more specific event types,
 */

// id cannot be passed manually
public class Event implements TimeTracking {
    public static final String RECURRENCE_NONE = "NONE";
    public static final String RECURRENCE_DAILY = "DAILY";
    public static final String RECURRENCE_WEEKLY = "WEEKLY";
    public static final String RECURRENCE_MONTHLY = "MONTHLY";

   private int id;
    private String title;
   private  String description;
   private  LocalDate date;
  private   LocalTime startTime;
   private LocalTime endTime;
    private String recurrencePattern;


    //Interface methods
    //setTime and getTime are not self-explanatory or easy to identify so they are setStartTime
    // and getStartTime
    @Override
    public void setStartTime(LocalTime time) {
        this.startTime = time;
    }
    @Override
    public void setDate(LocalDate date){
        this.date = date;
    }
    @Override
    public LocalTime getStartTime(){
        return this.startTime;
    }
    @Override
    public LocalDate getDate(){
        return  this.date;
    }


    //Prefered way of editing an existing event.
    public void editEvent(String newTitle,
                          String newDescription,
                          LocalDate newDate, LocalTime newStartTime,
                          LocalTime newEndTime, boolean recurring) {
        editEvent(
            newTitle,
            newDescription,
            newDate,
            newStartTime,
            newEndTime,
            recurring ? RECURRENCE_WEEKLY : RECURRENCE_NONE
        );
    }

    public void editEvent(String newTitle,
                          String newDescription,
                          LocalDate newDate, LocalTime newStartTime,
                          LocalTime newEndTime, String recurrencePattern) {

        this.title = newTitle;
        this.description = newDescription;
        this.date = newDate;
        this.startTime = newStartTime;
        this.endTime = newEndTime;
        setRecurrencePattern(recurrencePattern);

        // Needed validation so see if our Time set-up is valid
        if(newEndTime.isBefore(this.startTime)){
            throw new IllegalArgumentException("End time cannot be before start time.");
        }
    }





    // Boilerplate
    public Event(int id, String title, String description,
                 LocalDate date, LocalTime startTime,
                 LocalTime endTime, boolean recurring) {
        this(
            id,
            title,
            description,
            date,
            startTime,
            endTime,
            recurring ? RECURRENCE_WEEKLY : RECURRENCE_NONE
        );
    }

    public Event(int id, String title, String description,
                 LocalDate date, LocalTime startTime,
                 LocalTime endTime, String recurrencePattern) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.date = date;
        this.startTime = startTime;
        this.endTime = endTime;
        this.recurrencePattern = normalizeRecurrencePattern(recurrencePattern);
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalTime endTime) {
        this.endTime = endTime;
    }

    public boolean isRecurring() {
        return !RECURRENCE_NONE.equals(recurrencePattern);
    }

    public void setRecurring(boolean recurring) {
        this.recurrencePattern = recurring ? RECURRENCE_WEEKLY : RECURRENCE_NONE;
    }

    public String getRecurrencePattern() {
        return recurrencePattern;
    }

    public void setRecurrencePattern(String recurrencePattern) {
        this.recurrencePattern = normalizeRecurrencePattern(recurrencePattern);
    }

    public String recurrenceLabel() {
        return switch (recurrencePattern) {
            case RECURRENCE_DAILY -> "daily";
            case RECURRENCE_WEEKLY -> "weekly";
            case RECURRENCE_MONTHLY -> "monthly";
            default -> "none";
        };
    }

    private static String normalizeRecurrencePattern(String recurrencePattern) {
        String normalized = recurrencePattern == null ? "" : recurrencePattern.trim().toUpperCase();
        return switch (normalized) {
            case RECURRENCE_DAILY, RECURRENCE_WEEKLY, RECURRENCE_MONTHLY -> normalized;
            default -> RECURRENCE_NONE;
        };
    }

    // For debugging Purpose
    @Override
    public String toString() {
        return "org.Event{" +
                "id=" + id +
                ", title='" + title + '\'' +
                ", date=" + date +
                ", startTime=" + startTime +
                ", endTime=" + endTime +
                ", recurrencePattern='" + recurrencePattern + '\'' +
                '}';
    }
}
