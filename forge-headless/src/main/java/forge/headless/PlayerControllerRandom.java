package forge.headless;

import forge.LobbyPlayer;
import forge.game.Game;
import forge.game.player.Player;
import forge.util.MyRandom;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;

/**
 * Agent that makes random valid choices.
 * Useful for fuzz testing and exploring different game paths.
 */
public class PlayerControllerRandom extends PlayerControllerTUI {

    public PlayerControllerRandom(Game game, Player p, LobbyPlayer lp, boolean askMana, boolean numericChoices) {
        super(game, p, lp, askMana, numericChoices, new RandomChoiceReader());
    }

    @Override
    public boolean isAI() {
        return true; // Act like an AI for game logic purposes
    }

    /**
     * A BufferedReader that generates random numeric choices when readLine() is called.
     */
    private static class RandomChoiceReader extends BufferedReader {
        private static final int MAX_ATTEMPTS_BEFORE_ZERO = 20;
        private int attemptCount = 0;

        public RandomChoiceReader() {
            super(new EmptyReader());
        }

        @Override
        public String readLine() throws IOException {
            // Generate random choices, but bail out with "0" or "done" if we've tried too many times
            // This prevents infinite loops when invalid inputs are repeatedly rejected

            attemptCount++;

            // Every 20 attempts, return "0" or "done" to escape potential infinite loops
            if (attemptCount % MAX_ATTEMPTS_BEFORE_ZERO == 0) {
                // Alternate between "0" and "done"
                return (attemptCount / MAX_ATTEMPTS_BEFORE_ZERO) % 2 == 0 ? "0" : "done";
            }

            // 30% chance to pass/skip
            if (MyRandom.getRandom().nextInt(100) < 30) {
                return MyRandom.getRandom().nextBoolean() ? "0" : "done";
            }

            // Generate a random number between 0 and 5 (covers most common choice ranges)
            int choice = MyRandom.getRandom().nextInt(6);
            return String.valueOf(choice);
        }

        /**
         * Dummy Reader that does nothing
         */
        private static class EmptyReader extends Reader {
            @Override
            public int read(char[] cbuf, int off, int len) throws IOException {
                return -1; // EOF
            }

            @Override
            public void close() throws IOException {
                // No-op
            }
        }
    }
}
