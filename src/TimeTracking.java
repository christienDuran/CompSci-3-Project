/*
Interface for handling date and time information for objects which either keep track of the current date/time or have a
date/time associated with them.
 */
import java.time.*;

public interface TimeTracking {
    public void setTime(LocalTime time);
    public void setDate(LocalDate date);
    public LocalTime getTime();
    public LocalDate getDate();
}
