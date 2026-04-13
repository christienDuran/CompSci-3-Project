import java.time.*;

/*
Template for storing data for a calendar event. Generic superclass for more specific event types,
 */

// id cannot be passed manually
public class Event implements TimeTracking {
   private int id;
    private String title;
   private  String description;
   private  LocalDate date;
  private   LocalTime startTime;
   private LocalTime endTime;
    private boolean recurring;


    //Interface methods
    //setTime and getTime are not self-explanatory or easy to identify so they are setStartTime
    // and getStartTime
    public void setStartTime(LocalTime time) {
        this.startTime = time;
    }
    public void setDate(LocalDate date){
        this.date = date;
    }
    public LocalTime getStartTime(){
        return this.startTime;
    }
    public LocalDate getDate(){
        return  this.date;
    }


    //Prefered way of editing an existing event.
    public void editEvent(String newTitle,
                          String newDescription,
                          LocalDate newDate, LocalTime newStartTime,
                          LocalTime newEndTime, boolean recurring) {

        this.title = newTitle;
        this.description = newDescription;
        this.date = newDate;
        this.startTime = newStartTime;
        this.endTime = newEndTime;
        this.recurring = recurring;

        // Needed validation so see if our Time set-up is valid
        if(newEndTime.isBefore(this.startTime)){
            throw new IllegalArgumentException("End time cannot be before start time.");
        }
    }





    // Boilerplate
    public Event(int id, String title, String description,
                 LocalDate date, LocalTime startTime,
                 LocalTime endTime, boolean recurring) {
        this.id = id;
        this.title = title;
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
        return recurring;
    }

    public void setRecurring(boolean recurring) {
        this.recurring = recurring;
    }

    // For debugging Purpose
    @Override
    public String toString() {
        return "Event{" +
                "id=" + id +
                ", title='" + title + '\'' +
                ", date=" + date +
                ", startTime=" + startTime +
                ", endTime=" + endTime +
                ", recurring=" + recurring +
                '}';
    }
}
