
/*
For creating Budgets that will display in the calendar as progress bars. Has a max value, a current value, and a name.
The current value can be incremented by any amount but stays between the max value and zero. Every addition to the
current value should be tracked with a short description of what the expense was.
 */
// is supposed to allow the User to track their finances.
// Are we going to allow the user to add their own fields like "Food", "Mortgage", "Groceries", or are we going to add it?

import java.time.LocalDate;
// a grid to simply pop-up for the user to add their info into

public class Budget {
    private String expenseName;
    private double amount;
    private LocalDate date; // allow the user to set a time when an expense needs to be paid
}

// Constructor
public void Budget(String expenseName, double amount, LocalDate date) {
    this.expenseName = expenseName;
    this.amount = amount;
    this.date = date;
}

void main() {
}

//Methods
