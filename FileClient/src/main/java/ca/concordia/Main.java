package ca.concordia;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Scanner;

// Press Shift twice to open the Search Everywhere dialog and type `show whitespaces`,
// then press Enter. You can now see whitespace characters in your code.
public class Main {
    public static void main(String[] args) {
        //Socket CLient
        System.out.println("Hello and welcome!");
        Scanner scanner = new Scanner(System.in);

        try{
            Socket clientSocket = new Socket("localhost", 12345);
            System.out.println("Connected to the server at localhost:12345");

            //read user input from console
            String userInput = scanner.nextLine();
            try (
                    BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                    PrintWriter writer = new PrintWriter(clientSocket.getOutputStream(), true)
            ) {
                while (userInput != null && !userInput.isEmpty() && !userInput.equalsIgnoreCase("exit") && !userInput.equalsIgnoreCase("quit")) {
                    writer.println(userInput);
                    System.out.println("Message sent to the server: " + userInput);
                    //get response
                    String response = reader.readLine();
                    System.out.println("Response from server: " + response);

                    userInput = scanner.nextLine(); // Read next line
                }

                // Close the socket
                clientSocket.close();
                System.out.println("Connection closed.");
            }catch (Exception e) {
                e.printStackTrace();
            } finally {
                scanner.close();
            }


        }catch(Exception e) {
            e.printStackTrace();
        }
    }
}