package com.duyvo.pe26.tttbasic.Week08;

import com.duyvo.pe26.tttbasic.Board;
import com.duyvo.pe26.tttbasic.Game;

import java.io.IOException;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Week 08
 * Function explanation: Accept one client at a time and run one Tic-Tac-Toe game for that client.
 * Function/class call to: ServerSocket, Socket, Game, Board.
 * Function/class reference from: Week08.Client connects to this server.
 * Difference from previous week: Moves the existing terminal game behind a TCP server without threads.
 * What to check for when debugging: Port conflicts, client disconnects, and whether the game prompt reaches the client.
 */
public class Server {

    private static final int DEFAULT_PORT = 5000;

    /**
     * Function explanation: Parse the optional port and start the server.
     * Function/class call to: parsePort and start.
     * Function/class reference from: The JVM calls this method.
     * Difference from previous week: Adds a network server entry point.
     * What to check for when debugging: Ensure the command contains at most one numeric port.
     */
    public static void main(String[] args) {
        new Server().start(parsePort(args));
    }

    /**
     * Function explanation: Accept clients sequentially so only one game is active at a time.
     * Function/class call to: ServerSocket.accept and runGame.
     * Function/class reference from: main.
     * Difference from previous week: Keeps the server alive after each game.
     * What to check for when debugging: The accept loop must continue after a client quits.
     */
    public void start(int port) {
        try (ServerSocket serverSocket = new ServerSocket()) {
            serverSocket.setReuseAddress(true);
            serverSocket.bind(new InetSocketAddress(port));

            System.out.println("Week08 server listening on port " + port);
            System.out.println("Only one client can play at a time.");

            while (true) {
                try (Socket clientSocket = serverSocket.accept()) {
                    System.out.println("Client connected: " + clientSocket.getRemoteSocketAddress());
                    runGame(clientSocket);
                    System.out.println("Game ended; waiting for the next client.");
                } catch (IOException e) {
                    System.err.println("Client session error: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("Server startup error: " + e.getMessage());
        }
    }

    /**
     * Function explanation: Bind the socket streams directly to the unchanged Game class.
     * Function/class call to: Game.Game and Game.start.
     * Function/class reference from: start.
     * Difference from previous week: Reuses Game without replacing System.in or System.out and without a subprocess.
     * What to check for when debugging: Auto-flush must be enabled so prompts are sent immediately.
     */
    private void runGame(Socket clientSocket) throws IOException {
        PrintStream clientOutput = new PrintStream(
                clientSocket.getOutputStream(),
                true,
                StandardCharsets.UTF_8
        );

        Game game = new Game(
                Board.HUMAN_PLAYER,
                clientSocket.getInputStream(),
                clientOutput
        );
        game.start();
    }

    /**
     * Function explanation: Validate an optional TCP port.
     * Function/class call to: Integer.parseInt.
     * Function/class reference from: main.
     * Difference from previous week: Allows the network port to be selected at startup.
     * What to check for when debugging: Valid ports are between 1 and 65535.
     */
    private static int parsePort(String[] args) {
        if (args == null || args.length == 0) {
            return DEFAULT_PORT;
        }

        if (args.length != 1) {
            System.err.println("Usage: Server [port]");
            return DEFAULT_PORT;
        }

        try {
            int port = Integer.parseInt(args[0]);
            return port >= 1 && port <= 65535 ? port : DEFAULT_PORT;
        } catch (NumberFormatException e) {
            return DEFAULT_PORT;
        }
    }
}
