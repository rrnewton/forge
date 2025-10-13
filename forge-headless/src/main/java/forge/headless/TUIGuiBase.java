package forge.headless;

import forge.game.Game;
import forge.game.GameLogEntry;
import forge.gui.GuiBase;

import java.util.ArrayList;
import java.util.List;

/**
 * Extended HeadlessGuiBase for TUI mode that provides game log access.
 */
public class TUIGuiBase extends HeadlessGuiBase {

    private static volatile Game currentGame = null;
    private static volatile int lastLogIndex = 0;

    /**
     * Install the TUI GUI base as the global interface.
     */
    public static void install() {
        GuiBase.setInterface(new TUIGuiBase());
    }

    /**
     * Set the current game being played in TUI mode.
     */
    public static void setCurrentGame(Game game) {
        currentGame = game;
        lastLogIndex = 0;
    }

    /**
     * Get new log entries since the last check.
     * Returns entries with the +++ prefix for easy identification.
     */
    public static List<String> getNewLogEntries() {
        List<String> newEntries = new ArrayList<>();

        if (currentGame == null) {
            return newEntries;
        }

        List<GameLogEntry> allEntries = currentGame.getGameLog().getLogEntries(null);

        // Get entries from lastLogIndex onwards
        for (int i = lastLogIndex; i < allEntries.size(); i++) {
            GameLogEntry entry = allEntries.get(i);
            // Prefix with +++ for TUI log entries
            newEntries.add("+++ " + entry.toString());
        }

        lastLogIndex = allEntries.size();
        return newEntries;
    }

    /**
     * Print any new log entries to stdout.
     */
    public static void printNewLogEntries() {
        for (String entry : getNewLogEntries()) {
            System.out.println(entry);
        }
    }
}
