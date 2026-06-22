package com.duyvo.pe26.tttbasic.Week11;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Week 11
 * Function explanation: Return the exact signed board token with a human move within ten seconds.
 * Function/class call to: Socket, BufferedReader, BufferedWriter.
 * Function/class reference from: Week11.Server issues STATE tokens and validates returned MOVE tokens.
 * Difference from previous week: Carries issuedAt, nonce, and HMAC in addition to the board.
 * What to check for when debugging: Never alter token fields; enter the move before the token expires.
 */
public class Client {

    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_PORT = 5000;

    /**
     * Function explanation: Resolve connection arguments and start the signed-token loop.
     * Function/class call to: runClient and parsePort.
     * Function/class reference from: The JVM.
     * Difference from previous week: Connects to the Week11 signed-state server.
     * What to check for when debugging: Use Client [host] [port].
     */
    public static void main(String[] args) {
        String host = args != null && args.length >= 1 ? args[0] : DEFAULT_HOST;
        runClient(host, parsePort(args));
    }

    /**
     * Function explanation: Parse STATE/END lines and send MOVE board issuedAt nonce hash position.
     * Function/class call to: displayBoard and sendLine.
     * Function/class reference from: main.
     * Difference from previous week: Echoes the complete authenticated state token unchanged.
     * What to check for when debugging: STATE has six fields and END has three fields.
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
                String[] parts = response.trim().split("\\s+");

                if (parts.length == 3 && "END".equals(parts[0])) {
                    displayBoard(parts[1]);
                    System.out.println(parts[2].replace('_', ' '));
                    return;
                }

                if (parts.length != 6 || !"STATE".equals(parts[0])) {
                    System.err.println("Malformed server response: " + response);
                    return;
                }

                String board = parts[1];
                String issuedAt = parts[2];
                String nonce = parts[3];
                String hash = parts[4];
                String message = parts[5];

                displayBoard(board);
                System.out.println(message.replace('_', ' '));
                System.out.print("Choose a position [1-9] within 10 seconds, or q: ");
                String move = keyboard.readLine();
                if (move == null || "q".equalsIgnoreCase(move.trim())) {
                    return;
                }

                sendLine(serverOutput, "MOVE " + board + " " + issuedAt + " "
                        + nonce + " " + hash + " " + move.trim());
            }
        } catch (IOException e) {
            System.err.println("Client error: " + e.getMessage());
        }
    }

    /**
     * Function explanation: Render nine board digits in three rows.
     * Function/class call to: String.charAt.
     * Function/class reference from: runClient.
     * Difference from previous week: Rendering is unchanged.
     * What to check for when debugging: Board must match [012]{9}.
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
     * Function explanation: Write and flush one MOVE protocol line.
     * Function/class call to: BufferedWriter.write, newLine, and flush.
     * Function/class reference from: runClient.
     * Difference from previous week: The line now contains the signed token fields.
     * What to check for when debugging: Delaying flush may cause a move timeout.
     */
    private static void sendLine(BufferedWriter output, String line) throws IOException {
        output.write(line);
        output.newLine();
        output.flush();
    }

    /**
     * Function explanation: Parse the optional client port.
     * Function/class call to: Integer.parseInt.
     * Function/class reference from: main.
     * Difference from previous week: Command syntax remains Client [host] [port].
     * What to check for when debugging: Invalid values use 5000.
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
