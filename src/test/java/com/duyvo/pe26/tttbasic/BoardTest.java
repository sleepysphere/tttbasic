package com.duyvo.pe26.tttbasic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BoardTest {

    @Test
    void newBoardStartsEmptyAndRendersAsThreeByThreeValuesOnly() {
        Board board = new Board();

        assertEquals("0 0 0" + System.lineSeparator()
                + "0 0 0" + System.lineSeparator()
                + "0 0 0", board.render());
    }

    @Test
    void acceptsValidMoveAndUpdatesCell() {
        Board board = new Board();

        assertTrue(board.playMove(new Move(5), Board.HUMAN_PLAYER));

        assertEquals(Board.HUMAN_PLAYER, board.getCell(1, 1));
        assertEquals("0 0 0" + System.lineSeparator()
                + "0 1 0" + System.lineSeparator()
                + "0 0 0", board.render());
    }

    @Test
    void rejectsMoveOutsideBoard() {
        Board board = new Board();

        assertFalse(board.playMove(new Move(10), Board.HUMAN_PLAYER));
        assertFalse(board.playMove(new Move(-1, 0), Board.HUMAN_PLAYER));
        assertFalse(board.playMove(new Move(0, 3), Board.HUMAN_PLAYER));
    }

    @Test
    void rejectsUnknownPlayerValues() {
        Board board = new Board();

        assertFalse(board.playMove(new Move(1), 9));
        assertEquals(Board.EMPTY, board.getCell(0, 0));
    }

    @Test
    void rejectsOccupiedCellWithoutOverwritingOriginalValue() {
        Board board = new Board();

        assertTrue(board.playMove(new Move(1), Board.HUMAN_PLAYER));
        assertFalse(board.playMove(new Move(1), Board.COMPUTER_PLAYER));

        assertEquals(Board.HUMAN_PLAYER, board.getCell(0, 0));
    }

    @Test
    void detectsRowWinner() {
        Board board = new Board();
        board.playMove(new Move(1), Board.HUMAN_PLAYER);
        board.playMove(new Move(2), Board.HUMAN_PLAYER);
        board.playMove(new Move(3), Board.HUMAN_PLAYER);

        assertEquals(Board.HUMAN_PLAYER, board.checkWinner());
    }

    @Test
    void detectsColumnWinner() {
        Board board = new Board();
        board.playMove(new Move(2), Board.COMPUTER_PLAYER);
        board.playMove(new Move(5), Board.COMPUTER_PLAYER);
        board.playMove(new Move(8), Board.COMPUTER_PLAYER);

        assertEquals(Board.COMPUTER_PLAYER, board.checkWinner());
    }

    @Test
    void detectsDiagonalWinner() {
        Board board = new Board();
        board.playMove(new Move(1), Board.HUMAN_PLAYER);
        board.playMove(new Move(5), Board.HUMAN_PLAYER);
        board.playMove(new Move(9), Board.HUMAN_PLAYER);

        assertEquals(Board.HUMAN_PLAYER, board.checkWinner());
    }

    @Test
    void detectsAntiDiagonalWinner() {
        Board board = new Board();
        board.playMove(new Move(3), Board.COMPUTER_PLAYER);
        board.playMove(new Move(5), Board.COMPUTER_PLAYER);
        board.playMove(new Move(7), Board.COMPUTER_PLAYER);

        assertEquals(Board.COMPUTER_PLAYER, board.checkWinner());
    }

    @Test
    void detectsDrawOnlyWhenBoardIsFullAndNobodyWon() {
        Board board = new Board();
        int[] playersByPosition = {
                Board.COMPUTER_PLAYER, Board.HUMAN_PLAYER, Board.COMPUTER_PLAYER,
                Board.HUMAN_PLAYER, Board.HUMAN_PLAYER, Board.COMPUTER_PLAYER,
                Board.HUMAN_PLAYER, Board.COMPUTER_PLAYER, Board.HUMAN_PLAYER
        };

        for (int position = 1; position <= 9; position++) {
            board.playMove(new Move(position), playersByPosition[position - 1]);
        }

        assertEquals(Board.EMPTY, board.checkWinner());
        assertTrue(board.isDraw());
    }

    @Test
    void getCellRejectsOutOfRangeCoordinates() {
        Board board = new Board();

        assertThrows(IllegalArgumentException.class, () -> board.getCell(-1, 0));
        assertThrows(IllegalArgumentException.class, () -> board.getCell(0, 3));
    }
}
