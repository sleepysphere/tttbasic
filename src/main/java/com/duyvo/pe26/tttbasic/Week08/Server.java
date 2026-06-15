package com.duyvo.pe26.tttbasic.Week08;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

public class Server {

    private static final int DEFAULT_PORT = 5000;

    private static final int EMPTY = 0;
    private static final int HUMAN = 1;
    private static final int COMPUTER = 2;

    public static void main(String[] args) {
        int port = parsePort(args);

        Server server = new Server();
        server.start(port);
    }

    private static int parsePort(String[] args) {
        if (args.length == 0) {
            return DEFAULT_PORT;
        }

        try {
            int port = Integer.parseInt(args[0]);

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

    public void start(int port) {
        try (ServerSocket serverSocket = new ServerSocket()) {
            serverSocket.setReuseAddress(true);
            serverSocket.bind(new InetSocketAddress(port), 50);

            System.out.println("Tic-Tac-Toe server is running on port " + port);
            System.out.println("Only one active game is handled at a time.");
            System.out.println("Waiting for clients...");

            while (true) {
                try (Socket clientSocket = serverSocket.accept()) {
                    System.out.println();
                    System.out.println("Client connected: " + clientSocket.getInetAddress());

                    handleClient(clientSocket);

                    System.out.println("Client disconnected. Server is ready for the next game.");
                } catch (IOException e) {
                    System.out.println("Connection error: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.out.println("Server failed to start: " + e.getMessage());
        }
    }

    private void handleClient(Socket clientSocket) throws IOException {
        BufferedReader input = new BufferedReader(
                new InputStreamReader(clientSocket.getInputStream())
        );

        PrintWriter output = new PrintWriter(clientSocket.getOutputStream(), true);

        int[] board = new int[9];

        sendMessage(output, "Hello!");
        sendMessage(output, "You are Player#1.");
        sendMessage(output, "The computer is Player#2.");
        sendMessage(output, "Commands: enter 1-9 to move, or q to quit.");
        sendBoard(output, board);

        while (true) {
            sendMessage(output, "Player#1's turn");
            sendPrompt(output);

            String command = input.readLine();

            if (command == null) {
                System.out.println("Client closed the connection unexpectedly.");
                return;
            }

            if (command.equals("q")) {
                sendMessage(output, "End of the game");
                sendGameOver(output);
                return;
            }

            Integer position = parseMove(command);

            if (position == null) {
                sendMessage(output, "Please, input a valid number [1-9]");
                continue;
            }

            int humanIndex = position - 1;

            if (board[humanIndex] != EMPTY) {
                sendMessage(output, "The cell is occupied!");
                continue;
            }

            board[humanIndex] = HUMAN;
            sendBoard(output, board);

            if (hasWinner(board, HUMAN)) {
                sendMessage(output, "Player#1 won!");
                sendGameOver(output);
                return;
            }

            if (isDraw(board)) {
                sendMessage(output, "It is a draw!");
                sendGameOver(output);
                return;
            }

            sendMessage(output, "Player#2's turn");

            int computerIndex = chooseComputerMove(board);

            if (computerIndex != -1) {
                board[computerIndex] = COMPUTER;
            }

            sendBoard(output, board);

            if (hasWinner(board, COMPUTER)) {
                sendMessage(output, "Player#2 won!");
                sendGameOver(output);
                return;
            }

            if (isDraw(board)) {
                sendMessage(output, "It is a draw!");
                sendGameOver(output);
                return;
            }
        }
    }

    private Integer parseMove(String command) {
        try {
            int position = Integer.parseInt(command);

            if (position < 1 || position > 9) {
                return null;
            }

            return position;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private int chooseComputerMove(int[] board) {
        for (int i = 0; i < board.length; i++) {
            if (board[i] == EMPTY) {
                return i;
            }
        }

        return -1;
    }

    private boolean hasWinner(int[] board, int player) {
        int[][] winningLines = {
                {0, 1, 2},
                {3, 4, 5},
                {6, 7, 8},

                {0, 3, 6},
                {1, 4, 7},
                {2, 5, 8},

                {0, 4, 8},
                {2, 4, 6}
        };

        for (int[] line : winningLines) {
            if (board[line[0]] == player
                    && board[line[1]] == player
                    && board[line[2]] == player) {
                return true;
            }
        }

        return false;
    }

    private boolean isDraw(int[] board) {
        for (int cell : board) {
            if (cell == EMPTY) {
                return false;
            }
        }

        return true;
    }

    private void sendMessage(PrintWriter output, String message) {
        output.println("MESSAGE " + message);
    }

    private void sendPrompt(PrintWriter output) {
        output.println("PROMPT");
    }

    private void sendGameOver(PrintWriter output) {
        output.println("GAME_OVER");
    }

    private void sendBoard(PrintWriter output, int[] board) {
        output.println("BOARD");

        output.println(board[0] + " " + board[1] + " " + board[2]);
        output.println(board[3] + " " + board[4] + " " + board[5]);
        output.println(board[6] + " " + board[7] + " " + board[8]);

        output.println("END_BOARD");
    }
}