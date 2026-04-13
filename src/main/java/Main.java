
package org.example;

import example.UserAccount;

import java.util.Scanner;

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
public class Main {
    public static void main(String[] args) {

        System.out.println("Welcome to the user accounts system. , Enter username and password.");



        Scanner userInput = new Scanner(System.in);

        System.out.println("Enter Username: ");
        String username = userInput.nextLine();

        System.out.println(" Now enter your password: ");
        String password = userInput.nextLine();


        UserAccount user = UserAccount.createAccount(username, password);

        user.saveToCSV();

        //System.out.println("Account created!");
        System.out.println("Account created! Your ID is: " + user.getAccountID());
    }
}