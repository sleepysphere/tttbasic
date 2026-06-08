package com.duyvo.pe26.tttbasic;

import java.io.PrintStream;

public class Board {

    public static final int SIZE = 3;
    public static final int EMPTY = 0;
    public static final int HUMAN_PLAYER = 1;
    public static final int COMPUTER_PLAYER = 2;

    private final int[][] cells;

    public Board() {
        cells = new int[SIZE][SIZE];
    }

    public boolean playMove(Move move, int player) {
        if (!isKnownPlayer(player) || !isValidMove(move)) {
            return false;
        }

        cells[move.getRow()][move.getCol()] = player;
        return true;
    }

    public boolean isValidMove(Move move) {
        if (move == null) {
            return false;
        }

        int row = move.getRow();
        int col = move.getCol();

        return isInsideBoard(row, col) && cells[row][col] == EMPTY;
    }

    public boolean isEmpty(int row, int col) {
        return isInsideBoard(row, col) && cells[row][col] == EMPTY;
    }

    public int getCell(int row, int col) {
        if (!isInsideBoard(row, col)) {
            throw new IllegalArgumentException("Row and column must be between 0 and 2.");
        }

        return cells[row][col];
    }

    public int checkWinner() {
        for (int i = 0; i < SIZE; i++) {
            if (hasThreeInLine(cells[i][0], cells[i][1], cells[i][2])) {
                return cells[i][0];
            }

            if (hasThreeInLine(cells[0][i], cells[1][i], cells[2][i])) {
                return cells[0][i];
            }
        }

        if (hasThreeInLine(cells[0][0], cells[1][1], cells[2][2])) {
            return cells[0][0];
        }

        if (hasThreeInLine(cells[0][2], cells[1][1], cells[2][0])) {
            return cells[0][2];
        }

        return EMPTY;
    }

    public boolean isDraw() {
        return checkWinner() == EMPTY && isFull();
    }

    public String render() {
        StringBuilder builder = new StringBuilder();

        for (int row = 0; row < SIZE; row++) {
            for (int col = 0; col < SIZE; col++) {
                if (col > 0) {
                    builder.append(' ');
                }
                builder.append(cells[row][col]);
            }

            if (row < SIZE - 1) {
                builder.append(System.lineSeparator());
            }
        }

        return builder.toString();
    }

    public void printBoard() {
        printBoard(System.out);
    }

    public void printBoard(PrintStream output) {
        output.println(render());
    }

    private boolean isFull() {
        for (int row = 0; row < SIZE; row++) {
            for (int col = 0; col < SIZE; col++) {
                if (cells[row][col] == EMPTY) {
                    return false;
                }
            }
        }

        return true;
    }

    private boolean isKnownPlayer(int player) {
        return player == HUMAN_PLAYER || player == COMPUTER_PLAYER;
    }

    private boolean isInsideBoard(int row, int col) {
        return row >= 0 && row < SIZE && col >= 0 && col < SIZE;
    }

    private boolean hasThreeInLine(int first, int second, int third) {
        return first != EMPTY && first == second && second == third;
    }
}
