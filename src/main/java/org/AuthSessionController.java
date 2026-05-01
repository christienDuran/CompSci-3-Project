package org;

/*
Handles account creation, login, and persisted-session lookup.
*/
public class AuthSessionController {

    public String createAccount(String username, String password) {
        UserAccount user = AuthService.createAccount(username, password);
        return "Account created. ID: " + user.getAccountID() + ". You can now login.";
    }

    public UserAccount login(String username, String password) {
        return AuthService.login(username, password);
    }

    public UserAccount tryRestorePersistedSession() {
        Integer accountId = CsvStorage.loadCurrentSessionAccountId();
        if (accountId == null) {
            return null;
        }

        UserAccount restoredUser = CsvStorage.findUserByAccountId(accountId);
        if (restoredUser == null) {
            CsvStorage.clearCurrentSession();
            return null;
        }

        return restoredUser;
    }

    public void saveCurrentSession(UserAccount user) {
        if (user == null) {
            return;
        }
        CsvStorage.saveCurrentSession(user.getAccountID());
    }

    public void clearCurrentSession() {
        CsvStorage.clearCurrentSession();
    }
}
