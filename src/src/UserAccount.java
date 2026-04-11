// When the app opens up, we want the user to be able to click create account
//          after clicking, they will be prompted to create a username and a password.

// When they make their username and password, an accountID number should be randomly generated(which will be 5 numbers)
// The accountID, username, and password will then be stored in a csv file

// The next step will be, having a pop-up Questionnaire asking the user about wanting recurring Events stored-
//  within their CalendarBook



public class UserAccount {

    // Fields
    private String username;
    private String password;
    private int accountID;
    private boolean isLoggedIn;

    // Constructor
    public UserAccount(String username, String password, int accountID) {
        this.username = username;
        this.password = password;
        this.accountID = accountID;
        this.isLoggedIn = false;
    }

    // Create Account
    public static UserAccount createAccount(String username, String password, int accountID) {
        return new UserAccount(username, password, accountID);
    }

    // Login method
    public boolean login(String username, String password) {
        if (this.username.equals(username) && this.password.equals(password)) {
            isLoggedIn = true;
            System.out.println("Login successful.");
            return true;
        } else {
            System.out.println("Invalid username or password.");
            return false;
        }
    }

    // Logout method
    public void logout() {
        if (isLoggedIn) {
            isLoggedIn = false;
            System.out.println("Logged out successfully.");
        } else {
            System.out.println("User is not logged in.");
        }
    }

    // Getters (might use for something)
    public String getUsername() {
        return username;
    }

    public int getAccountID() {
        return accountID;
    }


    public class Main {
        public static void main(String[] args) {


        }
    }
