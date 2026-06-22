package com.duyvo.pe26.tttbasic.Week10;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Week 10
 * Function explanation: Keep the current board and exchange one request and one response at a time.
 * Function/class call to: Socket, BufferedReader, BufferedWriter.
 * Function/class reference from: Week10.Server sends STATE and END messages.
 * Difference from previous week: Keeps the Week09 protocol so server-side validation is the main change.
 * What to check for when debugging: Every MOVE line must contain the latest board returned by the server.
 */
public class Client {

    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_PORT = 5000;

    /**
     * Function explanation: Resolve connection arguments and start the protocol loop.
     * Function/class call to: runClient and parsePort.
     * Function/class reference from: The JVM calls this method.
     * Difference from previous week: Connects to the Week10 validating NIO server.
     * What to check for when debugging: Use Client [host] [port].
     */
    public static void main(String[] args) {
        String host = args != null && args.length >= 1 ? args[0] : DEFAULT_HOST;
        runClient(host, parsePort(args));
    }

    /**
     * Function explanation: Read STATE/END responses and send MOVE board position requests.
     * Function/class call to: displayBoard and sendLine.
     * Function/class reference from: main.
     * Difference from previous week: The client carries board state while the server checks it against authoritative state.
     * What to check for when debugging: The server response must contain exactly three fields.
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
            String response;
            while ((response = serverInput.readLine()) != null) {
                String[] parts = response.split("\\s+", 3);
                if (parts.length != 3) {
                    System.err.println("Malformed server response: " + response);
                    return;
                }

                String type = parts[0];
                String board = parts[1];
                String message = parts[2];
                displayBoard(board);
                System.out.println(message.replace('_', ' '));

                if ("END".equals(type)) {
                    return;
                }
                if (!"STATE".equals(type)) {
                    System.err.println("Unknown response type: " + type);
                    return;
                }

                System.out.print("Choose a position [1-9], or q: ");
                String move = keyboard.readLine();
                if (move == null || "q".equalsIgnoreCase(move.trim())) {
                    return;
                }
                sendLine(serverOutput, "MOVE " + board + " " + move.trim());
            }
        } catch (IOException e) {
            System.err.println("Client error: " + e.getMessage());
        }
    }

    /**
     * Function explanation: Render the nine-digit board as three terminal rows.
     * Function/class call to: String.substring.
     * Function/class reference from: runClient.
     * Difference from previous week: The client renders protocol state locally.
     * What to check for when debugging: A valid encoded board contains nine digits.
     */
    private static void displayBoard(String board) {
        if (board == null || !board.matches("[012]{9}")) {
            System.out.println("Invalid board: " + board);
            return;
        }
        for (int row = 0; row < 3; row++) {
            int start = row * 3;
            System.out.println(board.charAt(start) + " "
                    + board.charAt(start + 1) + " "
                    + board.charAt(start + 2));
        }
    }

    /**
     * Function explanation: Send and flush one protocol line.
     * Function/class call to: BufferedWriter.write, newLine, and flush.
     * Function/class reference from: runClient.
     * Difference from previous week: Sends a structured MOVE command.
     * What to check for when debugging: Missing flush causes the server to wait indefinitely.
     */
    private static void sendLine(BufferedWriter output, String line) throws IOException {
        output.write(line);
        output.newLine();
        output.flush();
    }

    /**
     * Function explanation: Parse the optional port.
     * Function/class call to: Integer.parseInt.
     * Function/class reference from: main.
     * Difference from previous week: Keeps the same command-line format.
     * What to check for when debugging: Invalid values fall back to 5000.
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
