package com.duyvo.pe26.tttbasic.Week12.Secure;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

/**
 * Week 12 - Secure
 * Function explanation: Return signed board tokens through synchronous Java HttpClient requests.
 * Function/class call to: HttpClient, HttpRequest, HttpResponse.
 * Function/class reference from: Week12.Secure.Server exposes the signed /game endpoint.
 * Difference from previous week: Keeps Week11 token fields but transports them with GET and POST.
 * What to check for when debugging: Endpoint, HTTP status, token field order, and ten-second input limit.
 */
public class Client {

    private static final String DEFAULT_BASE_URL = "http://localhost:8081/game";

    /**
     * Function explanation: Resolve the endpoint and start the secure HTTP loop.
     * Function/class call to: runClient.
     * Function/class reference from: The JVM.
     * Difference from previous week: Accepts a URL instead of host and port.
     * What to check for when debugging: Example is http://localhost:8081/game.
     */
    public static void main(String[] args) {
        String baseUrl = args != null && args.length >= 1 ? args[0] : DEFAULT_BASE_URL;
        runClient(URI.create(baseUrl));
    }

    /**
     * Function explanation: GET a signed board, then POST the unchanged token plus a move.
     * Function/class call to: sendGet, sendPost, and displayBoard.
     * Function/class reference from: main.
     * Difference from previous week: Signed protocol lines are carried in HTTP bodies.
     * What to check for when debugging: STATE has six fields and END has three fields.
     */
    private static void runClient(URI endpoint) {
        HttpClient httpClient = HttpClient.newHttpClient();
        try (BufferedReader keyboard = new BufferedReader(new InputStreamReader(
                System.in, StandardCharsets.UTF_8))) {

            String response = sendGet(httpClient, endpoint);
            while (response != null) {
                String[] parts = response.trim().split("\\s+");

                if (parts.length == 3 && "END".equals(parts[0])) {
                    displayBoard(parts[1]);
                    System.out.println(parts[2].replace('_', ' '));
                    return;
                }

                if (parts.length != 6 || !"STATE".equals(parts[0])) {
                    System.err.println("Malformed response: " + response);
                    return;
                }

                displayBoard(parts[1]);
                System.out.println(parts[5].replace('_', ' '));
                System.out.print("Choose a position [1-9] within 10 seconds, or q: ");
                String move = keyboard.readLine();
                if (move == null || "q".equalsIgnoreCase(move.trim())) {
                    return;
                }

                String requestBody = "MOVE " + parts[1] + " " + parts[2] + " "
                        + parts[3] + " " + parts[4] + " " + move.trim();
                response = sendPost(httpClient, endpoint, requestBody);
            }
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            System.err.println("Client error: " + e.getMessage());
        }
    }

    /**
     * Function explanation: Request a newly signed empty board with HTTP GET.
     * Function/class call to: HttpClient.send.
     * Function/class reference from: runClient.
     * Difference from previous week: Initial raw TCP STATE becomes an HTTP GET response.
     * What to check for when debugging: The response should be HTTP 200 and begin STATE.
     */
    private static String sendGet(HttpClient client, URI endpoint)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(endpoint).GET().build();
        return send(client, request);
    }

    /**
     * Function explanation: Submit a signed MOVE token as text/plain with HTTP POST.
     * Function/class call to: HttpRequest.BodyPublishers.ofString.
     * Function/class reference from: runClient.
     * Difference from previous week: Token is no longer written to a persistent socket.
     * What to check for when debugging: Return every token field exactly as received.
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
     * Function explanation: Execute a synchronous HTTP request and return its successful body.
     * Function/class call to: HttpClient.send and BodyHandlers.ofString.
     * Function/class reference from: sendGet and sendPost.
     * Difference from previous week: Adds HTTP status validation around the Week11 protocol.
     * What to check for when debugging: Non-2xx responses include useful server text.
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
     * Function explanation: Render a nine-digit board in three rows.
     * Function/class call to: String.charAt.
     * Function/class reference from: runClient.
     * Difference from previous week: No rendering change.
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
