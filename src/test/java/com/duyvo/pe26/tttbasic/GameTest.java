package com.duyvo.pe26.tttbasic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class GameTest {

    @Test
    void rejectsInvalidFirstPlayer() {
        ByteArrayInputStream input = inputOf("");
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        assertThrows(IllegalArgumentException.class, () -> new Game(3, input, printStreamFor(output)));
    }

    @Test
    void startsWithHumanFirstWhenFirstPlayerIsOne() {
        String output = runGame(1, "q\n");

        assertTrue(output.startsWith("Hello!" + System.lineSeparator()
                + "0 0 0" + System.lineSeparator()
                + "0 0 0" + System.lineSeparator()
                + "0 0 0" + System.lineSeparator()
                + "Player#1's turn" + System.lineSeparator()));
    }

    @Test
    void startsWithComputerFirstWhenFirstPlayerIsTwo() {
        String output = runGame(2, "q\n");

        assertTrue(output.startsWith("Hello!" + System.lineSeparator()
                + "0 0 0" + System.lineSeparator()
                + "0 0 0" + System.lineSeparator()
                + "0 0 0" + System.lineSeparator()
                + "Player#2's turn" + System.lineSeparator()));
        assertTrue(output.contains("2 0 0" + System.lineSeparator()
                + "0 0 0" + System.lineSeparator()
                + "0 0 0"));
    }

    @Test
    void validHumanMoveUpdatesBoardThenComputerGetsTurn() {
        String output = runGame(1, "5\nq\n");

        assertTrue(output.contains("0 0 0" + System.lineSeparator()
                + "0 1 0" + System.lineSeparator()
                + "0 0 0" + System.lineSeparator()
                + "Player#2's turn"));
        assertTrue(output.contains("2 0 0" + System.lineSeparator()
                + "0 1 0" + System.lineSeparator()
                + "0 0 0" + System.lineSeparator()
                + "Player#1's turn"));
    }

    @Test
    void invalidNonIntegerInputShowsMessageAndKeepsHumanTurn() {
        String output = runGame(1, "abc\n@\n\nq\n");

        assertEquals(3, countOccurrences(output, Game.INVALID_MOVE_MESSAGE));
        assertTrue(output.endsWith("End of the game" + System.lineSeparator()));
    }

    @Test
    void uppercaseAndSpacedQAreInvalidButExactLowercaseQQuits() {
        String output = runGame(1, "Q\n q\nq \nq\n");

        assertEquals(3, countOccurrences(output, Game.INVALID_MOVE_MESSAGE));
        assertTrue(output.endsWith("End of the game" + System.lineSeparator()));
    }

    @Test
    void outOfRangeIntegerInputShowsMessageAndKeepsHumanTurn() {
        String output = runGame(1, "0\n10\n-3\nq\n");

        assertEquals(3, countOccurrences(output, Game.INVALID_MOVE_MESSAGE));
    }

    @Test
    void occupiedCellShowsMessageAndKeepsHumanTurn() {
        String output = runGame(1, "1\n1\n3\nq\n");

        assertTrue(output.contains(Game.OCCUPIED_CELL_MESSAGE + System.lineSeparator()
                + "Player#1's turn"));
    }

    @Test
    void humanCanWinByRow() {
        String output = runGame(1, "7\n8\n9\n");

        assertTrue(output.endsWith("Player#1 won!" + System.lineSeparator()));
    }

    @Test
    void humanCanWinByColumn() {
        String output = runGame(1, "3\n6\n9\n");

        assertTrue(output.endsWith("Player#1 won!" + System.lineSeparator()));
    }

    @Test
    void humanCanWinByDiagonal() {
        String output = runGame(1, "3\n5\n7\n");

        assertTrue(output.endsWith("Player#1 won!" + System.lineSeparator()));
    }

    @Test
    void computerCanWin() {
        String output = runGame(1, "5\n6\n8\n");

        assertTrue(output.endsWith("Player#2 won!" + System.lineSeparator()));
    }

    @Test
    void detectsDrawAfterHumanMove() {
        String output = runGame(1, "2\n4\n5\n7\n9\n");

        assertTrue(output.endsWith("It is a draw!" + System.lineSeparator()));
    }

    @Test
    void detectsDrawAfterComputerMove() {
        String output = runGame(2, "2\n4\n7\n9\n");

        assertTrue(output.endsWith("It is a draw!" + System.lineSeparator()));
    }

    @Test
    void remainsResponsiveAfterManyInvalidInputs() {
        StringBuilder input = new StringBuilder();
        for (int i = 0; i < 35; i++) {
            input.append(i % 2 == 0 ? "x" : "999").append('\n');
        }
        input.append("5\nq\n");

        String output = runGame(1, input.toString());

        assertEquals(35, countOccurrences(output, Game.INVALID_MOVE_MESSAGE));
        assertTrue(output.contains("0 1 0"));
        assertTrue(output.endsWith("End of the game" + System.lineSeparator()));
    }

    private String runGame(int firstPlayer, String inputText) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Game game = new Game(firstPlayer, inputOf(inputText), printStreamFor(output));

        game.start();

        return output.toString(StandardCharsets.UTF_8);
    }

    private static ByteArrayInputStream inputOf(String inputText) {
        return new ByteArrayInputStream(inputText.getBytes(StandardCharsets.UTF_8));
    }

    private static PrintStream printStreamFor(ByteArrayOutputStream output) {
        return new PrintStream(output, true, StandardCharsets.UTF_8);
    }

    private static int countOccurrences(String text, String needle) {
        int count = 0;
        int index = 0;

        while ((index = text.indexOf(needle, index)) != -1) {
            count++;
            index += needle.length();
        }

        return count;
    }
}
