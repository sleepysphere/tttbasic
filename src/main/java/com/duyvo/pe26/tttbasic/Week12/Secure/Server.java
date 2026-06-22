package com.duyvo.pe26.tttbasic.Week12.Secure;

import com.duyvo.pe26.tttbasic.Board;
import com.duyvo.pe26.tttbasic.ComputerPlayer;
import com.duyvo.pe26.tttbasic.Move;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Week 12 - Secure
 * Function explanation: Transport the Week11 signed-board protocol over HTTP.
 * Function/class call to: HttpServer, HttpExchange, Board, ComputerPlayer, Mac, SecureRandom.
 * Function/class reference from: Week12.Secure.Client and curl call the /game endpoint.
 * Difference from previous week: Keeps Week11 HMAC security while replacing raw TCP with HTTP.
 * What to check for when debugging: HMAC secret, field order, ten-second timeout, HTTP body, and port.
 */
public class Server {

    private static final int DEFAULT_PORT = 8081;
    private static final int MAX_REQUEST_BYTES = 4096;
    private static final long MOVE_TIMEOUT_MILLIS = 10_000L;
    private static final long MAX_FUTURE_CLOCK_SKEW_MILLIS = 1_000L;
    private static final String EMPTY_BOARD = "000000000";
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String SECRET_ENVIRONMENT_VARIABLE = "TTT_HMAC_SECRET";

    private final SecureRandom secureRandom = new SecureRandom();
    private final byte[] secret = loadSecret();

    /**
     * Function explanation: Start the secure HTTP server on an optional port.
     * Function/class call to: parsePort and start.
     * Function/class reference from: The JVM.
     * Difference from previous week: Uses HTTP while retaining signed state.
     * What to check for when debugging: Default secure port is 8081.
     */
    public static void main(String[] args) {
        new Server().start(parsePort(args));
    }

    /**
     * Function explanation: Create /game and execute handlers sequentially with a direct executor.
     * Function/class call to: HttpServer.create, createContext, setExecutor, and start.
     * Function/class reference from: main.
     * Difference from previous week: HttpServer replaces the NIO selector loop.
     * What to check for when debugging: Keep Runnable::run to avoid an application handler pool.
     */
    public void start(int port) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/game", this::handleGame);
            server.setExecutor(Runnable::run);
            server.start();
            System.out.println("Week12 secure HTTP server: http://localhost:" + port + "/game");
            System.out.println("Players have 10 seconds to return each signed board.");
        } catch (IOException e) {
            System.err.println("Server startup error: " + e.getMessage());
        }
    }

    /**
     * Function explanation: Issue a signed empty board for GET or verify and process a signed MOVE for POST.
     * Function/class call to: createState, readRequest, processMove, and sendResponse.
     * Function/class reference from: HttpServer invokes this method for /game.
     * Difference from previous week: One signed-token exchange maps to one HTTP request and response.
     * What to check for when debugging: GET has no body; POST body carries all token fields.
     */
    private void handleGame(HttpExchange exchange) throws IOException {
        try (exchange) {
            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 200, createState(EMPTY_BOARD, "YOUR_MOVE"));
                return;
            }

            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                String request = readRequest(exchange);
                if (request == null) {
                    sendResponse(exchange, 413, createState(EMPTY_BOARD, "RESTARTED_REQUEST_TOO_LARGE"));
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
     * Function explanation: Verify the token, enforce ten seconds, validate the move, and issue the next token.
     * Function/class call to: verifyToken, decodeBoard, Board.playMove, ComputerPlayer.chooseMove, createState.
     * Function/class reference from: handleGame for POST.
     * Difference from previous week: Security logic is unchanged; only transport is HTTP.
     * What to check for when debugging: Request is MOVE board issuedAt nonce hash position.
     */
    private String processMove(String request) {
        String[] parts = request.trim().split("\\s+");
        if (parts.length != 6 || !"MOVE".equals(parts[0])) {
            return createState(EMPTY_BOARD, "RESTARTED_BAD_REQUEST");
        }

        String encodedBoard = parts[1];
        Long issuedAt = parseLong(parts[2]);
        String nonce = parts[3];
        String returnedHash = parts[4];
        Integer position = parsePosition(parts[5]);

        if (issuedAt == null || !verifyToken(encodedBoard, issuedAt, nonce, returnedHash)) {
            return createState(EMPTY_BOARD, "RESTARTED_INVALID_HASH");
        }

        long age = System.currentTimeMillis() - issuedAt;
        if (age > MOVE_TIMEOUT_MILLIS || age < -MAX_FUTURE_CLOCK_SKEW_MILLIS) {
            return createState(EMPTY_BOARD, "RESTARTED_MOVE_TIMEOUT");
        }

        Board board = decodeBoard(encodedBoard);
        if (board == null) {
            return createState(EMPTY_BOARD, "RESTARTED_INVALID_BOARD");
        }

        if (position == null || !board.playMove(new Move(position), Board.HUMAN_PLAYER)) {
            return createState(encodedBoard, "INVALID_MOVE");
        }

        String end = createEndResponse(board);
        if (end != null) {
            return end;
        }

        ComputerPlayer computer = new ComputerPlayer();
        board.playMove(computer.chooseMove(board), Board.COMPUTER_PLAYER);
        end = createEndResponse(board);
        return end != null ? end : createState(encodeBoard(board), "YOUR_MOVE");
    }

    /**
     * Function explanation: Create board, timestamp, nonce, HMAC, and message fields for one STATE response.
     * Function/class call to: SecureRandom, Base64, and sign.
     * Function/class reference from: GET handling and all continuing-game responses.
     * Difference from previous week: Token format is deliberately identical to Week11.
     * What to check for when debugging: The secret remains server-only.
     */
    private String createState(String encodedBoard, String message) {
        long issuedAt = System.currentTimeMillis();
        byte[] nonceBytes = new byte[16];
        secureRandom.nextBytes(nonceBytes);
        String nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(nonceBytes);
        String hash = sign(encodedBoard, issuedAt, nonce);
        return "STATE " + encodedBoard + " " + issuedAt + " " + nonce + " " + hash + " " + message;
    }

    /**
     * Function explanation: Verify the returned keyed hash in constant time.
     * Function/class call to: sign, Base64.Decoder, and MessageDigest.isEqual.
     * Function/class reference from: processMove before board reconstruction.
     * Difference from previous week: Unchanged from Week11.
     * What to check for when debugging: A plain hash is forgeable; this must remain HMAC-SHA256.
     */
    private boolean verifyToken(String board, long issuedAt, String nonce, String returnedHash) {
        if (board == null || !board.matches("[012]{9}") || nonce == null || returnedHash == null) {
            return false;
        }
        try {
            byte[] expected = Base64.getUrlDecoder().decode(sign(board, issuedAt, nonce));
            byte[] actual = Base64.getUrlDecoder().decode(returnedHash);
            return MessageDigest.isEqual(expected, actual);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Function explanation: Sign board, timestamp, and nonce with HMAC-SHA256.
     * Function/class call to: Mac.getInstance, SecretKeySpec, and Mac.doFinal.
     * Function/class reference from: createState and verifyToken.
     * Difference from previous week: Unchanged cryptographic payload and algorithm.
     * What to check for when debugging: Field order and separator must match verification exactly.
     */
    private String sign(String board, long issuedAt, String nonce) {
        String payload = board + "|" + issuedAt + "|" + nonce;
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
            byte[] signature = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC is unavailable", e);
        }
    }

    /**
     * Function explanation: Read a configured secret or create a random secret for this process.
     * Function/class call to: System.getenv and SecureRandom.nextBytes.
     * Function/class reference from: The secret field initializer.
     * Difference from previous week: Same secret policy as Week11.
     * What to check for when debugging: Use the same TTT_HMAC_SECRET when repeatable tokens are needed.
     */
    private byte[] loadSecret() {
        String configured = System.getenv(SECRET_ENVIRONMENT_VARIABLE);
        if (configured != null && !configured.isBlank()) {
            return configured.getBytes(StandardCharsets.UTF_8);
        }
        byte[] generated = new byte[32];
        secureRandom.nextBytes(generated);
        System.out.println("Warning: generated a temporary HMAC secret for this server run.");
        return generated;
    }

    /**
     * Function explanation: Read a bounded UTF-8 HTTP body.
     * Function/class call to: InputStream.readNBytes.
     * Function/class reference from: handleGame.
     * Difference from previous week: HTTP body replaces a raw TCP line.
     * What to check for when debugging: Requests above 4096 bytes receive HTTP 413.
     */
    private String readRequest(HttpExchange exchange) throws IOException {
        byte[] body = exchange.getRequestBody().readNBytes(MAX_REQUEST_BYTES + 1);
        if (body.length > MAX_REQUEST_BYTES) {
            return null;
        }
        return new String(body, StandardCharsets.UTF_8);
    }

    /**
     * Function explanation: Send one text/plain HTTP response with an explicit status.
     * Function/class call to: HttpExchange.sendResponseHeaders and response body write.
     * Function/class reference from: handleGame.
     * Difference from previous week: Adds HTTP metadata around the existing protocol line.
     * What to check for when debugging: Content-Length uses UTF-8 byte count.
     */
    private void sendResponse(HttpExchange exchange, int status, String response) throws IOException {
        byte[] bytes = (response + "\n").getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    /**
     * Function explanation: Reconstruct an authenticated board.
     * Function/class call to: Board.playMove and Move.
     * Function/class reference from: processMove after token verification.
     * Difference from previous week: Unchanged from Week11.
     * What to check for when debugging: Validate [012]{9} before reconstruction.
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
     * Function explanation: Convert Board cells to nine digits.
     * Function/class call to: Board.getCell.
     * Function/class reference from: processMove and createEndResponse.
     * Difference from previous week: Token board representation is unchanged.
     * What to check for when debugging: Use row-major order.
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
     * Function explanation: Encode a final win or draw response.
     * Function/class call to: Board.checkWinner and Board.isDraw.
     * Function/class reference from: processMove.
     * Difference from previous week: Final protocol content remains unchanged over HTTP.
     * What to check for when debugging: END responses have exactly three fields.
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
     * Function explanation: Parse positions 1 through 9.
     * Function/class call to: Integer.parseInt.
     * Function/class reference from: processMove.
     * Difference from previous week: Unchanged.
     * What to check for when debugging: Invalid values return null.
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
     * Function explanation: Parse the token issue timestamp.
     * Function/class call to: Long.parseLong.
     * Function/class reference from: processMove.
     * Difference from previous week: Unchanged.
     * What to check for when debugging: Malformed timestamps restart the game.
     */
    private Long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Function explanation: Parse the optional secure HTTP port.
     * Function/class call to: Integer.parseInt.
     * Function/class reference from: main.
     * Difference from previous week: Default is 8081 so insecure and secure servers can run together.
     * What to check for when debugging: Invalid values use 8081.
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
