// is supposed to allow the User to track their finances.
// Are we going to allow the user to add their own fields like "Food", "Mortgage", "Groceries", or are we going to add it?

import java.time.LocalDate;
import java.util.ArrayList; // will need this if we are going to integrate an addexpense method call, unless we want
                            // a grid to simply pop-up for the user to add their info into

public class Budget {
    private String expenseName;
    private double amount;
    private LocalDate date; // allow the user to set a time when an expense needs to be paid

}

    // Constructor
    public Budget(String expenseName, double amount, LocalDate date) {
        this.expenseName = expenseName;
        this.amount = amount;
        this.date = date;
    }

    //Methods
















