package com.duyvo.pe26.tttbasic.Week10;

import com.duyvo.pe26.tttbasic.Board;
import com.duyvo.pe26.tttbasic.ComputerPlayer;
import com.duyvo.pe26.tttbasic.Move;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

/**
 * Week 10
 * Function explanation: Run many games in one event loop while keeping an authoritative board per connection.
 * Function/class call to: Selector, SocketChannel, Board, ComputerPlayer, Move.
 * Function/class reference from: Week10.Client returns the board it most recently received.
 * Difference from previous week: The server no longer trusts the client's board and compares it with server state.
 * What to check for when debugging: A modified client board must produce TAMPER_DETECTED.
 */
public class Server {

    private static final int DEFAULT_PORT = 5000;
    private static final int MAX_LINE_LENGTH = 4096;

    /**
     * Function explanation: Start the server on an optional port.
     * Function/class call to: parsePort and start.
     * Function/class reference from: The JVM.
     * Difference from previous week: Package changes to Week10; command syntax remains stable.
     * What to check for when debugging: Use Server [port].
     */
    public static void main(String[] args) {
        new Server().start(parsePort(args));
    }

    /**
     * Function explanation: Process all network readiness events on one application thread.
     * Function/class call to: acceptClient, readClient, and writeClient.
     * Function/class reference from: main.
     * Difference from previous week: Each key attachment now includes an authoritative Board.
     * What to check for when debugging: No blocking read or write belongs inside this loop.
     */
    public void start(int port) {
        try (Selector selector = Selector.open();
             ServerSocketChannel serverChannel = ServerSocketChannel.open()) {

            serverChannel.configureBlocking(false);
            serverChannel.bind(new InetSocketAddress(port));
            serverChannel.register(selector, SelectionKey.OP_ACCEPT);
            System.out.println("Week10 secure single-threaded server listening on port " + port);

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
     * Function explanation: Register a client with a new authoritative empty board.
     * Function/class call to: ClientState and queueLine.
     * Function/class reference from: start on OP_ACCEPT.
     * Difference from previous week: Game state is now retained by the server for this connection.
     * What to check for when debugging: Every accepted socket must get a separate ClientState instance.
     */
    private void acceptClient(Selector selector, ServerSocketChannel serverChannel) throws IOException {
        SocketChannel channel = serverChannel.accept();
        if (channel == null) {
            return;
        }
        channel.configureBlocking(false);
        ClientState state = new ClientState();
        SelectionKey key = channel.register(selector, SelectionKey.OP_READ, state);
        queueLine(key, "STATE " + encodeBoard(state.board) + " YOUR_MOVE");
    }

    /**
     * Function explanation: Assemble complete request lines from non-blocking TCP reads.
     * Function/class call to: processLine and closeKey.
     * Function/class reference from: start on OP_READ.
     * Difference from previous week: Transport behavior is intentionally unchanged.
     * What to check for when debugging: Preserve partial lines between readiness events.
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
     * Function explanation: Flush queued response bytes without blocking other clients.
     * Function/class call to: SocketChannel.write.
     * Function/class reference from: start on OP_WRITE.
     * Difference from previous week: No functional change.
     * What to check for when debugging: Disable OP_WRITE when the queue becomes empty.
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
     * Function explanation: Compare the returned board with server state before validating and applying a move.
     * Function/class call to: encodeBoard, Board.playMove, ComputerPlayer.chooseMove, and createEndResponse.
     * Function/class reference from: readClient.
     * Difference from previous week: Forged boards are rejected instead of being reconstructed and trusted.
     * What to check for when debugging: Request format remains MOVE board position.
     */
    private void processLine(SelectionKey key, String line) {
        ClientState state = (ClientState) key.attachment();
        String authoritativeBoard = encodeBoard(state.board);
        String[] parts = line.trim().split("\\s+");

        if (parts.length != 3 || !"MOVE".equals(parts[0])) {
            queueLine(key, "STATE " + authoritativeBoard + " BAD_REQUEST");
            return;
        }
        if (!parts[1].matches("[012]{9}") || !parts[1].equals(authoritativeBoard)) {
            queueLine(key, "STATE " + authoritativeBoard + " TAMPER_DETECTED");
            return;
        }

        Integer position = parsePosition(parts[2]);
        if (position == null || !state.board.playMove(new Move(position), Board.HUMAN_PLAYER)) {
            queueLine(key, "STATE " + authoritativeBoard + " INVALID_MOVE");
            return;
        }

        String end = createEndResponse(state.board);
        if (end != null) {
            queueLine(key, end);
            return;
        }

        ComputerPlayer computer = new ComputerPlayer();
        state.board.playMove(computer.chooseMove(state.board), Board.COMPUTER_PLAYER);
        end = createEndResponse(state.board);
        queueLine(key, end != null
                ? end
                : "STATE " + encodeBoard(state.board) + " YOUR_MOVE");
    }

    /**
     * Function explanation: Encode a completed game or return null while play should continue.
     * Function/class call to: Board.checkWinner and Board.isDraw.
     * Function/class reference from: processLine.
     * Difference from previous week: Works on the authoritative server board.
     * What to check for when debugging: Winner 1 is HUMAN_WIN and winner 2 is COMPUTER_WIN.
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
     * Function explanation: Convert an authoritative Board to its nine-digit wire form.
     * Function/class call to: Board.getCell.
     * Function/class reference from: Connection setup, validation, and responses.
     * Difference from previous week: The same representation is retained to minimize client changes.
     * What to check for when debugging: Row-major order must match positions 1 through 9.
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
     * Function explanation: Parse a legal human position.
     * Function/class call to: Integer.parseInt.
     * Function/class reference from: processLine.
     * Difference from previous week: Same validation retained.
     * What to check for when debugging: Only 1 through 9 are valid.
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
     * Function explanation: Queue one response line for non-blocking output.
     * Function/class call to: ByteBuffer.wrap and SelectionKey.interestOps.
     * Function/class reference from: All response paths.
     * Difference from previous week: No functional change.
     * What to check for when debugging: Always append a newline.
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
     * Function explanation: Remove a failed or disconnected channel from the selector.
     * Function/class call to: SelectionKey.cancel and channel.close.
     * Function/class reference from: Network error paths.
     * Difference from previous week: Also releases the connection's authoritative Board.
     * What to check for when debugging: A disconnected game must not leak its attachment.
     */
    private void closeKey(SelectionKey key) {
        try {
            key.cancel();
            key.channel().close();
        } catch (IOException ignored) {
            // Channel cleanup is best-effort.
        }
    }

    /**
     * Function explanation: Parse the optional TCP port.
     * Function/class call to: Integer.parseInt.
     * Function/class reference from: main.
     * Difference from previous week: No command-line change.
     * What to check for when debugging: Invalid values use 5000.
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
     * Week 10 - Per-connection state
     * Function explanation: Store transport buffers plus the server's authoritative board.
     * Function/class call to: Board, ByteBuffer, StringBuilder, Deque.
     * Function/class reference from: SelectionKey attachments.
     * Difference from previous week: Adds Board, making Week10 secure but stateful.
     * What to check for when debugging: Never share one ClientState between sockets.
     */
    private static final class ClientState {
        private final Board board = new Board();
        private final ByteBuffer readBuffer = ByteBuffer.allocate(1024);
        private final StringBuilder incoming = new StringBuilder();
        private final Deque<ByteBuffer> outgoing = new ArrayDeque<>();
    }
}
