import java.time.*;

/*
Template for storing data for a calendar event. Generic superclass for more specific event types,
 */

public class Event implements TimeTracking {
    int id;
    String tittle;
    String description;
    LocalDate date;
    LocalTime startTime;
    LocalTime endTime;
    boolean recurring;

    //Interface methods

    public void setTime(LocalTime time) {
        this.startTime = time;
    }
    public void setDate(LocalDate date){
        this.date = date;
    }
    public LocalTime getTime(){
        return this.startTime;
    }
    public LocalDate getDate(){
        return  this.date;
    }

    //Prefered way of editing an existing event.

    public void editEvent(String newTittle, String newDescription, LocalDate newDate, LocalTime newStartTime, LocalTime newEndTime, boolean recurring) {

    }

    // Boilerplate

    public Event(int id, String tittle, String description, LocalDate date, LocalTime startTime, LocalTime endTime, boolean recurring) {
        this.id = id;
        this.tittle = tittle;
        this.description = description;
        this.date = date;
        this.startTime = startTime;
        this.endTime = endTime;
        this.recurring = recurring;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getTittle() {
        return tittle;
    }

    public void setTittle(String tittle) {
        this.tittle = tittle;
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
        return recurring;
    }

    public void setRecurring(boolean recurring) {
        this.recurring = recurring;
    }
}
