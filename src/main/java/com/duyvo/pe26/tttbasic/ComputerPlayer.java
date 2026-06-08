package com.duyvo.pe26.tttbasic;

public class ComputerPlayer {

    public Move chooseMove(Board board) {
        if (board == null) {
            throw new IllegalArgumentException("Board must not be null.");
        }

        for (int row = 0; row < Board.SIZE; row++) {
            for (int col = 0; col < Board.SIZE; col++) {
                if (board.isEmpty(row, col)) {
                    return new Move(row, col);
                }
            }
        }

        return new Move(-1, -1);
    }
}
