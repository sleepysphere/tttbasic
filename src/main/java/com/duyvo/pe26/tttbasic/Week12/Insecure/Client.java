package com.duyvo.pe26.tttbasic.Week12.Insecure;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

/**
 * Week 12 - Insecure
 * Function explanation: Play by sending synchronous GET and POST requests with Java HttpClient.
 * Function/class call to: HttpClient, HttpRequest, HttpResponse.
 * Function/class reference from: Week12.Insecure.Server exposes /game.
 * Difference from previous week: Replaces a persistent raw TCP socket with independent HTTP requests.
 * What to check for when debugging: Base URL, HTTP status, and MOVE request body.
 */
public class Client {

    private static final String DEFAULT_BASE_URL = "http://localhost:8080/game";

    /**
     * Function explanation: Resolve the endpoint and start the HTTP game loop.
     * Function/class call to: runClient.
     * Function/class reference from: The JVM.
     * Difference from previous week: Accepts a complete URL instead of separate host and port.
     * What to check for when debugging: Example argument is http://localhost:8080/game.
     */
    public static void main(String[] args) {
        String baseUrl = args != null && args.length >= 1 ? args[0] : DEFAULT_BASE_URL;
        runClient(URI.create(baseUrl));
    }

    /**
     * Function explanation: GET the initial state, then POST MOVE board position until END.
     * Function/class call to: sendGet, sendPost, and displayBoard.
     * Function/class reference from: main.
     * Difference from previous week: Each move uses a separate synchronous HTTP request.
     * What to check for when debugging: Response body still follows STATE board message or END board result.
     */
    private static void runClient(URI endpoint) {
        HttpClient httpClient = HttpClient.newHttpClient();
        try (BufferedReader keyboard = new BufferedReader(new InputStreamReader(
                System.in, StandardCharsets.UTF_8))) {

            String response = sendGet(httpClient, endpoint);
            while (response != null) {
                String[] parts = response.trim().split("\\s+", 3);
                if (parts.length != 3) {
                    System.err.println("Malformed response: " + response);
                    return;
                }

                displayBoard(parts[1]);
                System.out.println(parts[2].replace('_', ' '));
                if ("END".equals(parts[0])) {
                    return;
                }
                if (!"STATE".equals(parts[0])) {
                    System.err.println("Unexpected response type: " + parts[0]);
                    return;
                }

                System.out.print("Choose a position [1-9], or q: ");
                String move = keyboard.readLine();
                if (move == null || "q".equalsIgnoreCase(move.trim())) {
                    return;
                }
                response = sendPost(httpClient, endpoint,
                        "MOVE " + parts[1] + " " + move.trim());
            }
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            System.err.println("Client error: " + e.getMessage());
        }
    }

    /**
     * Function explanation: Request a new empty game with HTTP GET.
     * Function/class call to: HttpClient.send.
     * Function/class reference from: runClient.
     * Difference from previous week: Initial board comes from a GET response.
     * What to check for when debugging: The server should return HTTP 200.
     */
    private static String sendGet(HttpClient client, URI endpoint)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(endpoint).GET().build();
        return send(client, request);
    }

    /**
     * Function explanation: Submit one move as a text/plain HTTP POST body.
     * Function/class call to: HttpRequest.BodyPublishers.ofString and HttpClient.send.
     * Function/class reference from: runClient.
     * Difference from previous week: MOVE is no longer written to a socket stream.
     * What to check for when debugging: Content-Type should be text/plain.
     */
    private static String sendPost(HttpClient client, URI endpoint, String body)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .header("Content-Type", "text/plain; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        return send(client, request);
    }

    /**
     * Function explanation: Execute a synchronous HTTP request and require a 2xx response.
     * Function/class call to: HttpClient.send and BodyHandlers.ofString.
     * Function/class reference from: sendGet and sendPost.
     * Difference from previous week: HTTP status is validated separately from the protocol body.
     * What to check for when debugging: Print the status and body when a request fails.
     */
    private static String send(HttpClient client, HttpRequest request)
            throws IOException, InterruptedException {
        HttpResponse<String> response = client.send(
                request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("HTTP " + response.statusCode() + ": " + response.body());
        }
        return response.body().trim();
    }

    /**
     * Function explanation: Render a nine-digit board as three rows.
     * Function/class call to: String.charAt.
     * Function/class reference from: runClient.
     * Difference from previous week: Rendering remains unchanged.
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
}
