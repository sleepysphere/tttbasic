package com.duyvo.pe26.tttbasic.Week11;

import com.duyvo.pe26.tttbasic.Board;
import com.duyvo.pe26.tttbasic.ComputerPlayer;
import com.duyvo.pe26.tttbasic.Move;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayDeque;
import java.util.Base64;
import java.util.Deque;
import java.util.Iterator;

/**
 * Week 11
 * Function explanation: Validate a client-returned board with a server-keyed HMAC and a ten-second deadline.
 * Function/class call to: Selector, Board, ComputerPlayer, Mac, SecureRandom.
 * Function/class reference from: Week11.Client returns the exact state token received from this server.
 * Difference from previous week: Removes authoritative per-game Board state and replaces it with signed state tokens.
 * What to check for when debugging: HMAC secret, token field order, system clock, and ten-second expiry.
 */
public class Server {

    private static final int DEFAULT_PORT = 5000;
    private static final int MAX_LINE_LENGTH = 4096;
    private static final long MOVE_TIMEOUT_MILLIS = 10_000L;
    private static final long MAX_FUTURE_CLOCK_SKEW_MILLIS = 1_000L;
    private static final String EMPTY_BOARD = "000000000";
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String SECRET_ENVIRONMENT_VARIABLE = "TTT_HMAC_SECRET";

    private final SecureRandom secureRandom = new SecureRandom();
    private final byte[] secret = loadSecret();

    /**
     * Function explanation: Start the signed-state NIO server.
     * Function/class call to: parsePort and start.
     * Function/class reference from: The JVM.
     * Difference from previous week: Initializes a server-only signing key.
     * What to check for when debugging: Set TTT_HMAC_SECRET to keep tokens valid across server restarts.
     */
    public static void main(String[] args) {
        new Server().start(parsePort(args));
    }

    /**
     * Function explanation: Process all clients on one event-loop thread.
     * Function/class call to: acceptClient, readClient, and writeClient.
     * Function/class reference from: main.
     * Difference from previous week: Client attachments contain transport state only, not game state.
     * What to check for when debugging: The selector loop must never wait for terminal input or blocking socket I/O.
     */
    public void start(int port) {
        try (Selector selector = Selector.open();
             ServerSocketChannel serverChannel = ServerSocketChannel.open()) {

            serverChannel.configureBlocking(false);
            serverChannel.bind(new InetSocketAddress(port));
            serverChannel.register(selector, SelectionKey.OP_ACCEPT);
            System.out.println("Week11 signed-state server listening on port " + port);
            System.out.println("Players have 10 seconds to return each signed board.");

            while (true) {
                selector.select();
                Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();
                while (iterator.hasNext()) {
                    SelectionKey key = iterator.next();
                    iterator.remove();
                    try {
                        if (!key.isValid()) {
                            continue;
                        }
                        if (key.isAcceptable()) {
                            acceptClient(selector, serverChannel);
                        }
                        if (key.isReadable()) {
                            readClient(key);
                        }
                        if (key.isValid() && key.isWritable()) {
                            writeClient(key);
                        }
                    } catch (IOException e) {
                        closeKey(key);
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
        }
    }

    /**
     * Function explanation: Register a client and issue a signed empty board.
     * Function/class call to: createState and queueLine.
     * Function/class reference from: start on OP_ACCEPT.
     * Difference from previous week: No Board is stored in the connection attachment.
     * What to check for when debugging: Each initial token should have a different random nonce.
     */
    private void acceptClient(Selector selector, ServerSocketChannel serverChannel) throws IOException {
        SocketChannel channel = serverChannel.accept();
        if (channel == null) {
            return;
        }
        channel.configureBlocking(false);
        ClientState state = new ClientState();
        SelectionKey key = channel.register(selector, SelectionKey.OP_READ, state);
        queueLine(key, createState(EMPTY_BOARD, "YOUR_MOVE"));
    }

    /**
     * Function explanation: Assemble complete MOVE lines from non-blocking reads.
     * Function/class call to: processLine and closeKey.
     * Function/class reference from: start on OP_READ.
     * Difference from previous week: Transport handling remains intentionally unchanged.
     * What to check for when debugging: Reject overlong lines before they consume excessive memory.
     */
    private void readClient(SelectionKey key) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel();
        ClientState state = (ClientState) key.attachment();
        int bytesRead = channel.read(state.readBuffer);
        if (bytesRead == -1) {
            closeKey(key);
            return;
        }

        state.readBuffer.flip();
        while (state.readBuffer.hasRemaining()) {
            char character = (char) (state.readBuffer.get() & 0xff);
            if (character == '\n') {
                String line = state.incoming.toString();
                state.incoming.setLength(0);
                processLine(key, line);
            } else if (character != '\r') {
                state.incoming.append(character);
                if (state.incoming.length() > MAX_LINE_LENGTH) {
                    closeKey(key);
                    return;
                }
            }
        }
        state.readBuffer.clear();
    }

    /**
     * Function explanation: Flush queued protocol responses without blocking the event loop.
     * Function/class call to: SocketChannel.write.
     * Function/class reference from: start on OP_WRITE.
     * Difference from previous week: No functional change.
     * What to check for when debugging: Preserve partially written ByteBuffers.
     */
    private void writeClient(SelectionKey key) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel();
        ClientState state = (ClientState) key.attachment();
        while (!state.outgoing.isEmpty()) {
            ByteBuffer current = state.outgoing.peek();
            channel.write(current);
            if (current.hasRemaining()) {
                return;
            }
            state.outgoing.remove();
        }
        key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
    }

    /**
     * Function explanation: Verify the returned board token, enforce the deadline, validate the move, and issue a new token.
     * Function/class call to: verifyToken, decodeBoard, Board.playMove, ComputerPlayer.chooseMove, and createState.
     * Function/class reference from: readClient.
     * Difference from previous week: Trust comes from HMAC verification instead of retained server Board state.
     * What to check for when debugging: Request format is MOVE board issuedAt nonce hash position.
     */
    private void processLine(SelectionKey key, String line) {
        String[] parts = line.trim().split("\\s+");
        if (parts.length != 6 || !"MOVE".equals(parts[0])) {
            queueLine(key, createState(EMPTY_BOARD, "RESTARTED_BAD_REQUEST"));
            return;
        }

        String encodedBoard = parts[1];
        Long issuedAt = parseLong(parts[2]);
        String nonce = parts[3];
        String returnedHash = parts[4];
        Integer position = parsePosition(parts[5]);

        if (issuedAt == null || !verifyToken(encodedBoard, issuedAt, nonce, returnedHash)) {
            queueLine(key, createState(EMPTY_BOARD, "RESTARTED_INVALID_HASH"));
            return;
        }

        long age = System.currentTimeMillis() - issuedAt;
        if (age > MOVE_TIMEOUT_MILLIS || age < -MAX_FUTURE_CLOCK_SKEW_MILLIS) {
            queueLine(key, createState(EMPTY_BOARD, "RESTARTED_MOVE_TIMEOUT"));
            return;
        }

        Board board = decodeBoard(encodedBoard);
        if (board == null) {
            queueLine(key, createState(EMPTY_BOARD, "RESTARTED_INVALID_BOARD"));
            return;
        }

        if (position == null || !board.playMove(new Move(position), Board.HUMAN_PLAYER)) {
            queueLine(key, createState(encodedBoard, "INVALID_MOVE"));
            return;
        }

        String end = createEndResponse(board);
        if (end != null) {
            queueLine(key, end);
            return;
        }

        ComputerPlayer computer = new ComputerPlayer();
        board.playMove(computer.chooseMove(board), Board.COMPUTER_PLAYER);
        end = createEndResponse(board);
        queueLine(key, end != null ? end : createState(encodeBoard(board), "YOUR_MOVE"));
    }

    /**
     * Function explanation: Create a board token containing board, issue time, nonce, and keyed hash.
     * Function/class call to: SecureRandom, Base64, and sign.
     * Function/class reference from: Connection setup and all continuing-game responses.
     * Difference from previous week: The client can return server-authenticated state without the server storing the board.
     * What to check for when debugging: Never send the server secret to the client.
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
     * Function explanation: Recalculate the HMAC and compare it in constant time with the returned hash.
     * Function/class call to: sign, Base64.Decoder, and MessageDigest.isEqual.
     * Function/class reference from: processLine before trusting a returned board.
     * Difference from previous week: A client cannot create a valid hash for a modified board without the server key.
     * What to check for when debugging: Plain SHA-256 is insufficient because a cheating client can recompute it.
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
     * Function explanation: Produce HMAC-SHA256 over board, issue time, and nonce.
     * Function/class call to: Mac.getInstance, SecretKeySpec, and Mac.doFinal.
     * Function/class reference from: createState and verifyToken.
     * Difference from previous week: Adds cryptographic authenticity to the board state.
     * What to check for when debugging: Both signing and verification must use identical field separators and order.
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
     * Function explanation: Load a configured secret or generate a random server-lifetime secret.
     * Function/class call to: System.getenv and SecureRandom.nextBytes.
     * Function/class reference from: The secret field initializer.
     * Difference from previous week: Introduces server-only key material.
     * What to check for when debugging: Tokens issued before a restart fail unless TTT_HMAC_SECRET is configured consistently.
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
     * Function explanation: Reconstruct a board only after its HMAC has been verified.
     * Function/class call to: Board.playMove and Move.
     * Function/class reference from: processLine.
     * Difference from previous week: Reconstruction is safe because the encoded board is authenticated.
     * What to check for when debugging: Only nine digits from 0 through 2 are accepted.
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
     * Function explanation: Convert a Board into its stable nine-digit representation.
     * Function/class call to: Board.getCell.
     * Function/class reference from: processLine and createEndResponse.
     * Difference from previous week: Same representation is retained inside the signed token.
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
     * Function explanation: Return END when the game is won or drawn.
     * Function/class call to: Board.checkWinner and Board.isDraw.
     * Function/class reference from: processLine after human and computer moves.
     * Difference from previous week: Final responses do not need another signed state because no move follows.
     * What to check for when debugging: Final boards must still be displayed by the client.
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
     * Function explanation: Parse a legal move position.
     * Function/class call to: Integer.parseInt.
     * Function/class reference from: processLine.
     * Difference from previous week: Same move validation is retained.
     * What to check for when debugging: Accept only 1 through 9.
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
     * Function explanation: Parse a timestamp without throwing into the event loop.
     * Function/class call to: Long.parseLong.
     * Function/class reference from: processLine.
     * Difference from previous week: Supports the ten-second move deadline.
     * What to check for when debugging: A malformed timestamp restarts the game.
     */
    private Long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Function explanation: Queue one complete UTF-8 response line.
     * Function/class call to: ByteBuffer.wrap and SelectionKey.interestOps.
     * Function/class reference from: All protocol response paths.
     * Difference from previous week: Transport logic remains unchanged.
     * What to check for when debugging: Enable OP_WRITE after queuing data.
     */
    private void queueLine(SelectionKey key, String line) {
        if (!key.isValid()) {
            return;
        }
        ClientState state = (ClientState) key.attachment();
        state.outgoing.add(ByteBuffer.wrap((line + "\n").getBytes(StandardCharsets.UTF_8)));
        key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
    }

    /**
     * Function explanation: Close and unregister one failed client channel.
     * Function/class call to: SelectionKey.cancel and channel.close.
     * Function/class reference from: Disconnect and network error paths.
     * Difference from previous week: No game Board is lost because none is stored in the attachment.
     * What to check for when debugging: Closed channels must not remain selected.
     */
    private void closeKey(SelectionKey key) {
        try {
            key.cancel();
            key.channel().close();
        } catch (IOException ignored) {
            // Cleanup is best-effort.
        }
    }

    /**
     * Function explanation: Parse the optional server port.
     * Function/class call to: Integer.parseInt.
     * Function/class reference from: main.
     * Difference from previous week: Command syntax remains Server [port].
     * What to check for when debugging: Invalid values use port 5000.
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
     * Week 11 - Transport state only
     * Function explanation: Retain partial network input and pending output for one client.
     * Function/class call to: ByteBuffer, StringBuilder, and Deque.
     * Function/class reference from: Selector key attachments.
     * Difference from previous week: Removes the authoritative Board field.
     * What to check for when debugging: The absence of Board here is intentional for statelessness.
     */
    private static final class ClientState {
        private final ByteBuffer readBuffer = ByteBuffer.allocate(1024);
        private final StringBuilder incoming = new StringBuilder();
        private final Deque<ByteBuffer> outgoing = new ArrayDeque<>();
    }
}
