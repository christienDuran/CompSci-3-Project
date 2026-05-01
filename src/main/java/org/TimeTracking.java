package org;/*
Interface for handling date and time information for objects which either keep track of the current date/time or have a
date/time associated with them.
 */

import java.time.*;

interface TimeTracking {
    public void setStartTime(LocalTime time);
    public void setDate(LocalDate date);
    public LocalTime getStartTime();
    public LocalDate getDate();

}