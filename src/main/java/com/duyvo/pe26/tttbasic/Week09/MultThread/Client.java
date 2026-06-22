package com.duyvo.pe26.tttbasic.Week09.MultThread;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Week 09 - Exercise 9.01
 * Function explanation: Connect to the multithreaded server and play one game.
 * Function/class call to: Socket, BufferedReader, BufferedWriter.
 * Function/class reference from: A user starts this class; Server supplies the game stream.
 * Difference from previous week: The client behavior is intentionally almost identical to Week08.Client.
 * What to check for when debugging: The client waits for the exact human-turn prompt before sending input.
 */
public class Client {

    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_PORT = 5000;
    private static final String HUMAN_TURN_MESSAGE = "Player#1's turn";

    /**
     * Function explanation: Resolve connection arguments and start the client loop.
     * Function/class call to: runClient and parsePort.
     * Function/class reference from: The JVM calls this method.
     * Difference from previous week: Only the package name changes.
     * What to check for when debugging: Use Client [host] [port].
     */
    public static void main(String[] args) {
        String host = args != null && args.length >= 1 ? args[0] : DEFAULT_HOST;
        runClient(host, parsePort(args));
    }

    /**
     * Function explanation: Relay terminal input and output without creating client-side threads.
     * Function/class call to: Socket streams and System.in.
     * Function/class reference from: main.
     * Difference from previous week: Multiple copies of this client may now play concurrently.
     * What to check for when debugging: If no prompt appears, verify that the selected server mode accepted the client.
     */
    private static void runClient(String host, int port) {
        try (
                Socket socket = new Socket(host, port);
                BufferedReader serverInput = new BufferedReader(new InputStreamReader(
                        socket.getInputStream(), StandardCharsets.UTF_8));
                BufferedWriter serverOutput = new BufferedWriter(new OutputStreamWriter(
                        socket.getOutputStream(), StandardCharsets.UTF_8));
                BufferedReader keyboard = new BufferedReader(new InputStreamReader(
                        System.in, StandardCharsets.UTF_8))
        ) {
            System.out.println("Connected to " + host + ":" + port);

            String line;
            while ((line = serverInput.readLine()) != null) {
                System.out.println(line);
                if (HUMAN_TURN_MESSAGE.equals(line)) {
                    String input = keyboard.readLine();
                    serverOutput.write(input == null ? "q" : input);
                    serverOutput.newLine();
                    serverOutput.flush();
                }
            }
        } catch (IOException e) {
            System.err.println("Client error: " + e.getMessage());
        }
    }

    /**
     * Function explanation: Parse the optional port.
     * Function/class call to: Integer.parseInt.
     * Function/class reference from: main.
     * Difference from previous week: No behavioral change.
     * What to check for when debugging: Invalid values fall back to port 5000.
     */
    private static int parsePort(String[] args) {
        if (args == null || args.length < 2) {
            return DEFAULT_PORT;
        }
        try {
            int port = Integer.parseInt(args[1]);
            return port >= 1 && port <= 65535 ? port : DEFAULT_PORT;
        } catch (NumberFormatException e) {
            return DEFAULT_PORT;
        }
    }
}
