package forge.view;

import forge.game.Match;

/**
 * Deprecated: This class has been moved to forge.headless.SimulateMatch.
 * This wrapper class is maintained for backwards compatibility only.
 *
 * @deprecated Use {@link forge.headless.SimulateMatch} instead. This class will be removed in a future version.
 */
@Deprecated
public class SimulateMatch {
    /**
     * Simulates a match based on command line arguments.
     *
     * @param args Command line arguments for simulation
     * @deprecated Use {@link forge.headless.SimulateMatch#simulate(String[])} instead.
     */
    @Deprecated
    public static void simulate(String[] args) {
        forge.headless.SimulateMatch.simulate(args);
    }

    /**
     * Simulates a single match.
     *
     * @param mc The match to simulate
     * @param iGame The game number
     * @param outputGamelog Whether to output the full game log
     * @deprecated Use {@link forge.headless.SimulateMatch#simulateSingleMatch(Match, int, boolean)} instead.
     */
    @Deprecated
    public static void simulateSingleMatch(final Match mc, int iGame, boolean outputGamelog) {
        forge.headless.SimulateMatch.simulateSingleMatch(mc, iGame, outputGamelog);
    }
}