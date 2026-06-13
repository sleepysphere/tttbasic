package com.duyvo.pe26.tttbasic.Week8;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class Client {

    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_PORT = 5000;

    public static void main(String[] args) {
        String host = parseHost(args);
        int port = parsePort(args);

        Client client = new Client();
        client.start(host, port);
    }

    private static String parseHost(String[] args) {
        if (args.length >= 1) {
            return args[0];
        }

        return DEFAULT_HOST;
    }

    private static int parsePort(String[] args) {
        if (args.length < 2) {
            return DEFAULT_PORT;
        }

        try {
            int port = Integer.parseInt(args[1]);

            if (port < 1 || port > 65535) {
                System.out.println("Invalid port. Using default port " + DEFAULT_PORT);
                return DEFAULT_PORT;
            }

            return port;
        } catch (NumberFormatException e) {
            System.out.println("Invalid port. Using default port " + DEFAULT_PORT);
            return DEFAULT_PORT;
        }
    }

    public void start(String host, int port) {
        try (
                Socket socket = new Socket(host, port);
                BufferedReader serverInput = new BufferedReader(
                        new InputStreamReader(socket.getInputStream())
                );
                PrintWriter serverOutput = new PrintWriter(socket.getOutputStream(), true);
                BufferedReader keyboardInput = new BufferedReader(
                        new InputStreamReader(System.in)
                )
        ) {
            System.out.println("Connected to Tic-Tac-Toe server at " + host + ":" + port);

            String line;

            while ((line = serverInput.readLine()) != null) {
                if (line.startsWith("MESSAGE ")) {
                    System.out.println(line.substring("MESSAGE ".length()));
                } else if (line.equals("BOARD")) {
                    printBoard(serverInput);
                } else if (line.equals("PROMPT")) {
                    System.out.print("> ");

                    String command = keyboardInput.readLine();

                    if (command == null) {
                        serverOutput.println("q");
                    } else {
                        serverOutput.println(command);
                    }
                } else if (line.equals("GAME_OVER")) {
                    return;
                } else {
                    System.out.println(line);
                }
            }
        } catch (IOException e) {
            System.out.println("Client error: " + e.getMessage());
        }
    }

    private void printBoard(BufferedReader serverInput) throws IOException {
        String line;

        while ((line = serverInput.readLine()) != null) {
            if (line.equals("END_BOARD")) {
                break;
            }

            System.out.println(line);
        }
    }
}