package com.duyvo.pe26.tttbasic.Week12.Insecure;

import com.duyvo.pe26.tttbasic.Board;
import com.duyvo.pe26.tttbasic.ComputerPlayer;
import com.duyvo.pe26.tttbasic.Move;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/**
 * Week 12 - Insecure
 * Function explanation: Expose the Week09-style client-carried board protocol over HTTP.
 * Function/class call to: HttpServer, HttpExchange, Board, ComputerPlayer, Move.
 * Function/class reference from: Week12.Insecure.Client and curl call the /game endpoint.
 * Difference from previous week: Changes transport from raw TCP to HTTP and intentionally omits HMAC validation.
 * What to check for when debugging: HTTP method, request body, Content-Type, and port 8080.
 */
public class Server {

    private static final int DEFAULT_PORT = 8080;
    private static final int MAX_REQUEST_BYTES = 4096;
    private static final String EMPTY_BOARD = "000000000";

    /**
     * Function explanation: Start the HTTP server on an optional port.
     * Function/class call to: parsePort and start.
     * Function/class reference from: The JVM.
     * Difference from previous week: Uses an HTTP listening port by default.
     * What to check for when debugging: Use Server [port].
     */
    public static void main(String[] args) {
        new Server().start(parsePort(args));
    }

    /**
     * Function explanation: Create /game and use a direct executor so handlers run sequentially.
     * Function/class call to: HttpServer.create, createContext, setExecutor, and start.
     * Function/class reference from: main.
     * Difference from previous week: HttpServer replaces Selector and SocketChannel.
     * What to check for when debugging: Do not replace the direct executor with a thread pool for this exercise.
     */
    public void start(int port) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/game", this::handleGame);
            server.setExecutor(Runnable::run);
            server.start();
            System.out.println("Week12 insecure HTTP server: http://localhost:" + port + "/game");
        } catch (IOException e) {
            System.err.println("Server startup error: " + e.getMessage());
        }
    }

    /**
     * Function explanation: Return an empty board for GET or process one client-carried move for POST.
     * Function/class call to: readRequest, processMove, and sendResponse.
     * Function/class reference from: HttpServer invokes this method for /game.
     * Difference from previous week: One complete protocol exchange maps to one HTTP request and response.
     * What to check for when debugging: GET has no body; POST body is MOVE board position.
     */
    private void handleGame(HttpExchange exchange) throws IOException {
        try (exchange) {
            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 200, "STATE " + EMPTY_BOARD + " YOUR_MOVE");
                return;
            }

            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                String request = readRequest(exchange);
                if (request == null) {
                    sendResponse(exchange, 413, "STATE " + EMPTY_BOARD + " REQUEST_TOO_LARGE");
                    return;
                }
                sendResponse(exchange, 200, processMove(request));
                return;
            }

            exchange.getResponseHeaders().set("Allow", "GET, POST");
            sendResponse(exchange, 405, "METHOD_NOT_ALLOWED");
        }
    }

    /**
     * Function explanation: Trust the returned board, validate the requested position, then make the computer move.
     * Function/class call to: decodeBoard, Board.playMove, ComputerPlayer.chooseMove, and createEndResponse.
     * Function/class reference from: handleGame for POST.
     * Difference from previous week: Same insecure stateless logic is carried over from Week09 using HTTP.
     * What to check for when debugging: A malicious client can forge a board in this intentionally insecure version.
     */
    private String processMove(String request) {
        String[] parts = request.trim().split("\\s+");
        if (parts.length != 3 || !"MOVE".equals(parts[0])) {
            return "STATE " + EMPTY_BOARD + " BAD_REQUEST";
        }

        Board board = decodeBoard(parts[1]);
        Integer position = parsePosition(parts[2]);
        if (board == null || position == null) {
            return "STATE " + EMPTY_BOARD + " BAD_REQUEST";
        }
        if (!board.playMove(new Move(position), Board.HUMAN_PLAYER)) {
            return "STATE " + encodeBoard(board) + " INVALID_MOVE";
        }

        String end = createEndResponse(board);
        if (end != null) {
            return end;
        }

        ComputerPlayer computer = new ComputerPlayer();
        board.playMove(computer.chooseMove(board), Board.COMPUTER_PLAYER);
        end = createEndResponse(board);
        return end != null ? end : "STATE " + encodeBoard(board) + " YOUR_MOVE";
    }

    /**
     * Function explanation: Read a bounded UTF-8 HTTP request body.
     * Function/class call to: InputStream.readNBytes.
     * Function/class reference from: handleGame.
     * Difference from previous week: TCP line framing is replaced by the HTTP message body length.
     * What to check for when debugging: More than 4096 bytes returns HTTP 413.
     */
    private String readRequest(HttpExchange exchange) throws IOException {
        byte[] body = exchange.getRequestBody().readNBytes(MAX_REQUEST_BYTES + 1);
        if (body.length > MAX_REQUEST_BYTES) {
            return null;
        }
        return new String(body, StandardCharsets.UTF_8);
    }

    /**
     * Function explanation: Send one plain-text HTTP response.
     * Function/class call to: HttpExchange.sendResponseHeaders and response body write.
     * Function/class reference from: handleGame.
     * Difference from previous week: Adds HTTP status and Content-Type metadata.
     * What to check for when debugging: Content-Length must equal the UTF-8 byte length.
     */
    private void sendResponse(HttpExchange exchange, int status, String response) throws IOException {
        byte[] bytes = (response + "\n").getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    /**
     * Function explanation: Rebuild a Board from nine digits supplied by the client.
     * Function/class call to: Board.playMove and Move.
     * Function/class reference from: processMove.
     * Difference from previous week: Logic is unchanged; only transport differs.
     * What to check for when debugging: Only [012]{9} is accepted.
     */
    private Board decodeBoard(String encoded) {
        if (encoded == null || !encoded.matches("[012]{9}")) {
            return null;
        }
        Board board = new Board();
        for (int index = 0; index < encoded.length(); index++) {
            int player = encoded.charAt(index) - '0';
            if (player != Board.EMPTY) {
                board.playMove(new Move(index + 1), player);
            }
        }
        return board;
    }

    /**
     * Function explanation: Convert Board cells to nine protocol digits.
     * Function/class call to: Board.getCell.
     * Function/class reference from: processMove and createEndResponse.
     * Difference from previous week: Representation remains stable for minimal client changes.
     * What to check for when debugging: Row-major order maps to positions 1 through 9.
     */
    private String encodeBoard(Board board) {
        StringBuilder encoded = new StringBuilder(9);
        for (int row = 0; row < Board.SIZE; row++) {
            for (int column = 0; column < Board.SIZE; column++) {
                encoded.append(board.getCell(row, column));
            }
        }
        return encoded.toString();
    }

    /**
     * Function explanation: Encode HUMAN_WIN, COMPUTER_WIN, or DRAW when the game ends.
     * Function/class call to: Board.checkWinner and Board.isDraw.
     * Function/class reference from: processMove.
     * Difference from previous week: Same protocol response now travels in an HTTP body.
     * What to check for when debugging: Return null only while the game continues.
     */
    private String createEndResponse(Board board) {
        int winner = board.checkWinner();
        if (winner == Board.HUMAN_PLAYER) {
            return "END " + encodeBoard(board) + " HUMAN_WIN";
        }
        if (winner == Board.COMPUTER_PLAYER) {
            return "END " + encodeBoard(board) + " COMPUTER_WIN";
        }
        if (board.isDraw()) {
            return "END " + encodeBoard(board) + " DRAW";
        }
        return null;
    }

    /**
     * Function explanation: Parse a move from 1 through 9.
     * Function/class call to: Integer.parseInt.
     * Function/class reference from: processMove.
     * Difference from previous week: No functional change.
     * What to check for when debugging: Invalid text must not throw out of the HTTP handler.
     */
    private Integer parsePosition(String value) {
        try {
            int position = Integer.parseInt(value);
            return position >= 1 && position <= 9 ? position : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Function explanation: Parse the optional HTTP port.
     * Function/class call to: Integer.parseInt.
     * Function/class reference from: main.
     * Difference from previous week: Default changes from 5000 to 8080.
     * What to check for when debugging: Invalid values use 8080.
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
}
