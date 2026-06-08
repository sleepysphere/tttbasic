package com.duyvo.pe26.tttbasic;

import java.io.InputStream;
import java.io.PrintStream;
import java.util.Scanner;

public class Game {

    static final String GREETING_MESSAGE = "Hello!";
    static final String INVALID_MOVE_MESSAGE = "Please, input a valid number [1-9]";
    static final String OCCUPIED_CELL_MESSAGE = "The cell is occupied!";
    static final String QUIT_MESSAGE = "End of the game";
    static final String DRAW_MESSAGE = "It is a draw!";

    private final Board board;
    private final Scanner scanner;
    private final ComputerPlayer computer;
    private final PrintStream output;

    private int currentPlayer;

    public Game(int firstPlayer) {
        this(firstPlayer, System.in, System.out);
    }

    public Game(int firstPlayer, InputStream input, PrintStream output) {
        this(firstPlayer, new Board(), new Scanner(input), new ComputerPlayer(), output);
    }

    Game(int firstPlayer, Board board, Scanner scanner, ComputerPlayer computer, PrintStream output) {
        if (firstPlayer != Board.HUMAN_PLAYER && firstPlayer != Board.COMPUTER_PLAYER) {
            throw new IllegalArgumentException("First player must be 1 or 2.");
        }

        this.board = board;
        this.scanner = scanner;
        this.computer = computer;
        this.output = output;
        this.currentPlayer = firstPlayer;
    }

    public void start() {
        output.println(GREETING_MESSAGE);
        board.printBoard(output);

        while (true) {
            printTurnMessage();

            boolean shouldContinue = currentPlayer == Board.HUMAN_PLAYER
                    ? playHumanTurn()
                    : playComputerTurn();

            if (!shouldContinue) {
                return;
            }

            board.printBoard(output);

            if (printFinalStateIfGameOver()) {
                return;
            }

            switchCurrentPlayer();
        }
    }

    private boolean playHumanTurn() {
        while (true) {
            if (!scanner.hasNextLine()) {
                output.println(QUIT_MESSAGE);
                return false;
            }

            String input = scanner.nextLine();

            if ("q".equals(input)) {
                output.println(QUIT_MESSAGE);
                return false;
            }

            int position;

            try {
                position = Integer.parseInt(input);
            } catch (NumberFormatException e) {
                printInvalidHumanInputMessage();
                continue;
            }

            if (position < 1 || position > 9) {
                printInvalidHumanInputMessage();
                continue;
            }

            Move move = new Move(position);

            if (!board.playMove(move, Board.HUMAN_PLAYER)) {
                output.println(OCCUPIED_CELL_MESSAGE);
                printHumanTurnMessage();
                continue;
            }

            return true;
        }
    }

    private boolean playComputerTurn() {
        Move move = computer.chooseMove(board);
        board.playMove(move, Board.COMPUTER_PLAYER);
        return true;
    }

    private boolean printFinalStateIfGameOver() {
        int winner = board.checkWinner();

        if (winner != Board.EMPTY) {
            output.println("Player#" + winner + " won!");
            return true;
        }

        if (board.isDraw()) {
            output.println(DRAW_MESSAGE);
            return true;
        }

        return false;
    }

    private void switchCurrentPlayer() {
        currentPlayer = currentPlayer == Board.HUMAN_PLAYER
                ? Board.COMPUTER_PLAYER
                : Board.HUMAN_PLAYER;
    }

    private void printInvalidHumanInputMessage() {
        output.println(INVALID_MOVE_MESSAGE);
        printHumanTurnMessage();
    }

    private void printTurnMessage() {
        output.println("Player#" + currentPlayer + "'s turn");
    }

    private void printHumanTurnMessage() {
        output.println("Player#" + Board.HUMAN_PLAYER + "'s turn");
    }
}
