package com.duyvo.pe26.tttbasic.Week08;

import java.io.IOException;
import java.net.Socket;
import java.util.Scanner;
import java.io.InputStream;
import java.io.OutputStream;

public class Client {

    private static final String HOST = "localhost";
    private static final int PORT = 5000;

    public static void main(String[] args) {
        try (
                Socket socket = new Socket(HOST, PORT);
                InputStream serverInput = socket.getInputStream();
                OutputStream serverOutput = socket.getOutputStream();
                Scanner keyboard = new Scanner(System.in)
        ) {
            System.out.println("Connected to server.");

            Thread readerThread = new Thread(() -> {
                try {
                    int data;
                    while ((data = serverInput.read()) != -1) {
                        System.out.print((char) data);
                    }
                } catch (IOException e) {
                    System.out.println("\nDisconnected from server.");
                }
            });

            readerThread.setDaemon(true);
            readerThread.start();

            while (keyboard.hasNextLine()) {
                String input = keyboard.nextLine();

                serverOutput.write((input + System.lineSeparator()).getBytes());
                serverOutput.flush();
            }

        } catch (IOException e) {
            System.err.println("Client error: " + e.getMessage());
        }
    }
}