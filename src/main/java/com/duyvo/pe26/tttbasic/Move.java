package com.duyvo.pe26.tttbasic;

public class Move {

    private static final int FIRST_POSITION = 1;
    private static final int LAST_POSITION = 9;
    private static final int BOARD_SIZE = 3;

    private final int row;
    private final int col;

    public Move(int position) {
        if (position < FIRST_POSITION || position > LAST_POSITION) {
            row = -1;
            col = -1;
            return;
        }

        row = (position - 1) / BOARD_SIZE;
        col = (position - 1) % BOARD_SIZE;
    }

    public Move(int row, int col) {
        this.row = row;
        this.col = col;
    }

    public int getRow() {
        return row;
    }

    public int getCol() {
        return col;
    }
}
