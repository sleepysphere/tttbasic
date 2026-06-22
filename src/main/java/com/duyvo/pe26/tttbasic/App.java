package com.duyvo.pe26.tttbasic;

import java.io.InputStream;
import java.io.PrintStream;

public class App {

    static final String INVALID_STARTUP_ARGUMENT_MESSAGE = "Please, input a valid option [1-2]";

    public static void main(String[] args) {
        if (!hasValidStartupArguments(args)) {
            System.out.println(INVALID_STARTUP_ARGUMENT_MESSAGE);
            return;
        }

        int firstPlayer = Integer.parseInt(args[0]);
        Game game = new Game(firstPlayer);
        game.start();
    }

    static boolean hasValidStartupArguments(String[] args) {
        return args != null
                && args.length == 1
                && ("1".equals(args[0]) || "2".equals(args[0]));
    }
}
