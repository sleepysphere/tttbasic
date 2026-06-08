package com.duyvo.pe26.tttbasic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ComputerPlayerTest {

    @Test
    void choosesFirstCellOnEmptyBoard() {
        ComputerPlayer computer = new ComputerPlayer();
        Board board = new Board();

        Move move = computer.chooseMove(board);

        assertEquals(0, move.getRow());
        assertEquals(0, move.getCol());
    }

    @Test
    void choosesSmallestIndexedAvailableCell() {
        ComputerPlayer computer = new ComputerPlayer();
        Board board = new Board();
        board.playMove(new Move(1), Board.HUMAN_PLAYER);
        board.playMove(new Move(2), Board.COMPUTER_PLAYER);
        board.playMove(new Move(3), Board.HUMAN_PLAYER);

        Move move = computer.chooseMove(board);

        assertEquals(1, move.getRow());
        assertEquals(0, move.getCol());
    }

    @Test
    void returnsInvalidMoveWhenBoardIsFull() {
        ComputerPlayer computer = new ComputerPlayer();
        Board board = new Board();

        for (int position = 1; position <= 9; position++) {
            int player = position % 2 == 0 ? Board.HUMAN_PLAYER : Board.COMPUTER_PLAYER;
            board.playMove(new Move(position), player);
        }

        Move move = computer.chooseMove(board);

        assertFalse(board.isValidMove(move));
    }

    @Test
    void rejectsNullBoard() {
        ComputerPlayer computer = new ComputerPlayer();

        assertThrows(IllegalArgumentException.class, () -> computer.chooseMove(null));
    }
}
