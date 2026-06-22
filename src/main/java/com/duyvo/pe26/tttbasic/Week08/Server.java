package com.duyvo.pe26.tttbasic.Week08;

import com.duyvo.pe26.tttbasic.App;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

public class Server {
    /*
     * Fair semaphore:
     * Only 1 client can play at a time.
     * Other clients wait in connection order.
     */
    private static final int DEFAULT_PORT = 5000;

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
                    System.out.println("Client connected: " + clientSocket.getInetAddress() + ": "+ clientSocket.getPort());

                    runAppForClient(clientSocket);

                    System.out.println("Client disconnected. Server is ready for the next game.");
                } catch (IOException e) {
                    System.out.println("Connection error: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.out.println("Server failed to start: " + e.getMessage());
        }
    }

    private static void runAppForClient(Socket clientSocket) throws IOException {
        Process process = startAppProcess();

        Thread appOutputToClient = new Thread(() -> copy(
                process.getInputStream(),
                getSocketOutputStream(clientSocket)
        ), "app-output-to-client");

        Thread clientInputToApp = new Thread(() -> {
            copy(
                    getSocketInputStream(clientSocket),
                    process.getOutputStream()
            );

            /*
            * Important:
            * If the client disconnects, close App's stdin.
            * Otherwise App may keep waiting for input forever.
            */
            closeQuietly(process.getOutputStream());

            if (process.isAlive()) {
                process.destroy();
            }
        }, "client-input-to-app");

        appOutputToClient.setDaemon(true);
        clientInputToApp.setDaemon(true);

        appOutputToClient.start();
        clientInputToApp.start();

        try {
            process.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        } finally {
            if (process.isAlive()) {
                process.destroy();

                try {
                    if (!process.waitFor(500, TimeUnit.MILLISECONDS)) {
                        process.destroyForcibly();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    process.destroyForcibly();
                }
            }

            closeQuietly(process.getOutputStream());
            closeQuietly(process.getInputStream());
            closeQuietly(process.getErrorStream());

            try {
                sendMessage(clientSocket, "\nGame session ended.");
            } catch (IOException ignored) {
            }

            /*
            * Important:
            * Closing the socket tells the client that the session is over.
            * This also unblocks server-side socket reading threads.
            */
            closeQuietly(clientSocket);

            joinQuietly(appOutputToClient);
            joinQuietly(clientInputToApp);
        }
    }

    private static Process startAppProcess() throws IOException {
        String javaExecutable = Path.of(
                System.getProperty("java.home"),
                "bin",
                "java"
        ).toString();

        String classpath = System.getProperty("java.class.path");

        return new ProcessBuilder(
                javaExecutable,
                "-cp",
                classpath,
                App.class.getName(),
                "1"
        )
                /*
                * Combine stderr into stdout so only one thread writes to the client.
                * This avoids two server threads writing to the same socket output.
                */
                .redirectErrorStream(true)
                .start();
    }

    private static void closeQuietly(Closeable closeable) {
    if (closeable == null) {
        return;
    }

    try {
        closeable.close();
    } catch (IOException ignored) {
    }
}

    private static void closeQuietly(Socket socket) {
        if (socket == null) {
            return;
        }

        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }

    private static void joinQuietly(Thread thread) {
        if (thread == null) {
            return;
        }

        try {
            thread.join(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void copy(InputStream input, OutputStream output) {
        byte[] buffer = new byte[1024];

        try {
            int bytesRead;
            while ((bytesRead = input.read(buffer)) != -1) {
                output.write(buffer, 0, bytesRead);
                output.flush();
            }
        } catch (IOException ignored) {
        }
    }

    private static void sendMessage(Socket socket, String message) throws IOException {
        OutputStream output = socket.getOutputStream();
        output.write((message + System.lineSeparator()).getBytes());
        output.flush();
    }

    private static InputStream getSocketInputStream(Socket socket) {
        try {
            return socket.getInputStream();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static OutputStream getSocketOutputStream(Socket socket) {
        try {
            return socket.getOutputStream();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}