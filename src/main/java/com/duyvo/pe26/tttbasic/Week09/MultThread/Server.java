package com.duyvo.pe26.tttbasic.Week09.MultThread;

import com.duyvo.pe26.tttbasic.Board;
import com.duyvo.pe26.tttbasic.Game;

import java.io.IOException;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Week 09 - Exercise 9.01
 * Function explanation: Run either one-thread-per-game mode or a fixed four-thread-pool mode.
 * Function/class call to: ServerSocket, Thread, ThreadPoolExecutor, Game.
 * Function/class reference from: Week09.MultThread.Client connects to this server.
 * Difference from previous week: Several games may run concurrently.
 * What to check for when debugging: Confirm the mode argument and observe active thread names in server logs.
 */
public class Server {

    private static final int DEFAULT_PORT = 5000;
    private static final int POOL_SIZE = 4;
    private static final int WAITING_QUEUE_SIZE = 100;

    private enum Mode {
        UNBOUNDED,
        POOL
    }

    /**
     * Function explanation: Parse [port] [unbounded|pool] and start the chosen server mode.
     * Function/class call to: parsePort, parseMode, and start.
     * Function/class reference from: The JVM calls this method.
     * Difference from previous week: Adds a configurable concurrency policy.
     * What to check for when debugging: Example: Server 5000 pool.
     */
    public static void main(String[] args) {
        int port = parsePort(args);
        Mode mode = parseMode(args);
        new Server().start(port, mode);
    }

    /**
     * Function explanation: Accept sockets continuously and dispatch each game according to the selected mode.
     * Function/class call to: createPool, startUnboundedThread, and handleClient.
     * Function/class reference from: main.
     * Difference from previous week: The accept loop no longer waits for a game to finish.
     * What to check for when debugging: In pool mode, only four "ttt-pool" workers should be active.
     */
    public void start(int port, Mode mode) {
        ExecutorService pool = mode == Mode.POOL ? createPool() : null;

        try (ServerSocket serverSocket = new ServerSocket()) {
            serverSocket.setReuseAddress(true);
            serverSocket.bind(new InetSocketAddress(port));

            System.out.println("Week09 multithreaded server listening on port " + port);
            System.out.println("Mode: " + mode.name().toLowerCase(Locale.ROOT));

            while (true) {
                Socket clientSocket = serverSocket.accept();

                if (mode == Mode.UNBOUNDED) {
                    startUnboundedThread(clientSocket);
                } else {
                    pool.execute(new ClientTask(clientSocket, this));
                }
            }
        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
        } finally {
            if (pool != null) {
                pool.shutdownNow();
            }
        }
    }

    /**
     * Function explanation: Create exactly four workers and a bounded waiting queue.
     * Function/class call to: ThreadPoolExecutor and rejectClient.
     * Function/class reference from: start.
     * Difference from previous week: Limits concurrency and rejects overload instead of creating unlimited threads.
     * What to check for when debugging: A fifth active game must wait; excessive clients receive SERVER_BUSY.
     */
    private ExecutorService createPool() {
        return new ThreadPoolExecutor(
                POOL_SIZE,
                POOL_SIZE,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(WAITING_QUEUE_SIZE),
                runnable -> {
                    Thread thread = new Thread(runnable);
                    thread.setName("ttt-pool-" + thread.getId());
                    return thread;
                },
                (runnable, executor) -> {
                    if (runnable instanceof ClientTask clientTask) {
                        rejectClient(clientTask.socket());
                    }
                }
        );
    }

    /**
     * Function explanation: Start a dedicated platform thread for one game.
     * Function/class call to: Thread.start and handleClient.
     * Function/class reference from: start in unbounded mode.
     * Difference from previous week: Every accepted client receives its own thread.
     * What to check for when debugging: Large connection bursts intentionally create many threads and may exhaust resources.
     */
    private void startUnboundedThread(Socket clientSocket) {
        Thread thread = new Thread(() -> handleClient(clientSocket));
        thread.setName("ttt-pool-" + thread.getId());
        thread.start();
    }

    /**
     * Function explanation: Run the unchanged Game class using one client's socket streams.
     * Function/class call to: Game.Game and Game.start.
     * Function/class reference from: Worker threads created by start or the pool.
     * Difference from previous week: This method can execute concurrently for different clients.
     * What to check for when debugging: Each game must use only its own socket streams.
     */
    private void handleClient(Socket clientSocket) {
        try (clientSocket) {
            System.out.println(Thread.currentThread().getName() + " handling "
                    + clientSocket.getRemoteSocketAddress());

            PrintStream output = new PrintStream(
                    clientSocket.getOutputStream(),
                    true,
                    StandardCharsets.UTF_8
            );
            new Game(Board.HUMAN_PLAYER, clientSocket.getInputStream(), output).start();
        } catch (IOException e) {
            System.err.println("Client session error: " + e.getMessage());
        }
    }

    /**
     * Function explanation: Tell an excess client that the bounded pool cannot accept more work.
     * Function/class call to: Socket.getOutputStream and Socket.close.
     * Function/class reference from: The pool rejection handler.
     * Difference from previous week: Protects the server during very large connection bursts.
     * What to check for when debugging: The message must be flushed before the socket closes.
     */
    private void rejectClient(Socket socket) {
        try (socket; PrintStream output = new PrintStream(
                socket.getOutputStream(), true, StandardCharsets.UTF_8)) {
            output.println("SERVER_BUSY");
        } catch (IOException ignored) {
            // The peer may already have disconnected during overload.
        }
    }

    /**
     * Function explanation: Hold a client socket as an identifiable pool task.
     * Function/class call to: handleClient.
     * Function/class reference from: createPool's rejection handler can inspect this task.
     * Difference from previous week: Makes overload rejection able to close the correct socket.
     * What to check for when debugging: Always close rejected sockets.
     */
    private record ClientTask(Socket socket, Server server) implements Runnable {
        @Override
        public void run() {
            server.handleClient(socket);
        }
    }

    /**
     * Function explanation: Parse a valid TCP port.
     * Function/class call to: Integer.parseInt.
     * Function/class reference from: main.
     * Difference from previous week: Same behavior retained for minimal change.
     * What to check for when debugging: The first argument is the port.
     */
    private static int parsePort(String[] args) {
        if (args == null || args.length == 0) {
            return DEFAULT_PORT;
        }
        try {
            int port = Integer.parseInt(args[0]);
            return port >= 1 && port <= 65535 ? port : DEFAULT_PORT;
        } catch (NumberFormatException e) {
            return DEFAULT_PORT;
        }
    }

    /**
     * Function explanation: Parse unbounded or pool mode.
     * Function/class call to: String.toLowerCase.
     * Function/class reference from: main.
     * Difference from previous week: Selects the exercise's two required implementations.
     * What to check for when debugging: The second argument defaults to unbounded.
     */
    private static Mode parseMode(String[] args) {
        if (args == null || args.length < 2) {
            return Mode.UNBOUNDED;
        }
        return "pool".equalsIgnoreCase(args[1]) ? Mode.POOL : Mode.UNBOUNDED;
    }
}
