package com.duyvo.pe26.tttbasic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AppTest {

    private final PrintStream originalOut = System.out;
    private final java.io.InputStream originalIn = System.in;

    private ByteArrayOutputStream output;

    @BeforeEach
    void setUp() {
        output = new ByteArrayOutputStream();
        System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
    }

    @AfterEach
    void tearDown() {
        System.setOut(originalOut);
        System.setIn(originalIn);
    }

    @Test
    void rejectsMissingStartupArgument() {
        App.main(new String[] {});

        assertEquals(App.INVALID_STARTUP_ARGUMENT_MESSAGE + System.lineSeparator(), capturedOutput());
    }

    @Test
    void rejectsInvalidStartupArgumentsExactly() {
        String[][] invalidArgs = {
                { "0" },
                { "3" },
                { "-1" },
                { "abc" },
                { "01" },
                { " 1" },
                { "1 " },
                { "1", "2" }
        };

        for (String[] args : invalidArgs) {
            output.reset();
            App.main(args);
            assertEquals(App.INVALID_STARTUP_ARGUMENT_MESSAGE + System.lineSeparator(), capturedOutput());
        }
    }

    @Test
    void acceptsOnlyOneOrTwoAsStartupArgument() {
        assertTrue(App.hasValidStartupArguments(new String[] { "1" }));
        assertTrue(App.hasValidStartupArguments(new String[] { "2" }));
        assertFalse(App.hasValidStartupArguments(null));
        assertFalse(App.hasValidStartupArguments(new String[] {}));
        assertFalse(App.hasValidStartupArguments(new String[] { "01" }));
        assertFalse(App.hasValidStartupArguments(new String[] { "1", "2" }));
    }

    @Test
    void validHumanFirstRunCanQuitCleanly() {
        System.setIn(new ByteArrayInputStream("q\n".getBytes(StandardCharsets.UTF_8)));

        App.main(new String[] { "1" });

        String result = capturedOutput();
        assertTrue(result.startsWith("Hello!" + System.lineSeparator()
                + "0 0 0" + System.lineSeparator()
                + "0 0 0" + System.lineSeparator()
                + "0 0 0" + System.lineSeparator()
                + "Player#1's turn" + System.lineSeparator()));
        assertTrue(result.endsWith("End of the game" + System.lineSeparator()));
    }

    private String capturedOutput() {
        return output.toString(StandardCharsets.UTF_8);
    }
}
