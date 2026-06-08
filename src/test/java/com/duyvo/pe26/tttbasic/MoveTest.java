package com.duyvo.pe26.tttbasic;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MoveTest {

    @Test
    void mapsBoardPositionsToZeroBasedCoordinates() {
        assertMove(new Move(1), 0, 0);
        assertMove(new Move(2), 0, 1);
        assertMove(new Move(3), 0, 2);
        assertMove(new Move(4), 1, 0);
        assertMove(new Move(5), 1, 1);
        assertMove(new Move(6), 1, 2);
        assertMove(new Move(7), 2, 0);
        assertMove(new Move(8), 2, 1);
        assertMove(new Move(9), 2, 2);
    }

    @Test
    void invalidPositionsCreateInvalidCoordinates() {
        assertMove(new Move(0), -1, -1);
        assertMove(new Move(10), -1, -1);
        assertMove(new Move(-3), -1, -1);
    }

    private void assertMove(Move move, int expectedRow, int expectedCol) {
        assertEquals(expectedRow, move.getRow());
        assertEquals(expectedCol, move.getCol());
    }
}
