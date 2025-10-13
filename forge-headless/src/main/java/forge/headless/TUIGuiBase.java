package forge.headless;

import forge.game.Game;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * TUI-specific GuiBase that extends HeadlessGuiBase and adds text UI interaction.
 */
public class TUIGuiBase extends HeadlessGuiBase {

    private static final BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

    /**
     * Display the current game state in the TUI.
     */
    public static void displayGameState(Game game) {
        if (game == null || game.isGameOver()) {
            return;
        }

        System.out.println();
        System.out.println("=".repeat(60));

        // Turn and phase information
        Player turnPlayer = game.getPhaseHandler().getPlayerTurn();
        PhaseType phase = game.getPhaseHandler().getPhase();
        int turn = game.getPhaseHandler().getTurn();

        System.out.println("Turn " + turn + " - " + turnPlayer.getName() + "'s turn");
        System.out.println("Phase: " + phase.nameForUi);
        System.out.println();

        // Display all players' information
        for (Player p : game.getPlayers()) {
            displayPlayerInfo(p, game);
            System.out.println();
        }

        System.out.println("-".repeat(60));
    }

    private static void displayPlayerInfo(Player player, Game game) {
        boolean isActive = player == game.getPhaseHandler().getPlayerTurn();
        String marker = isActive ? ">>> " : "    ";

        System.out.println(marker + player.getName());
        System.out.println(marker + "  Life: " + player.getLife());
        System.out.println(marker + "  Hand: " + player.getZone(ZoneType.Hand).size() + " cards");
        System.out.println(marker + "  Library: " + player.getZone(ZoneType.Library).size() + " cards");
        System.out.println(marker + "  Graveyard: " + player.getZone(ZoneType.Graveyard).size() + " cards");

        // Show lands on battlefield
        List<Card> lands = new ArrayList<>();
        List<Card> creatures = new ArrayList<>();
        List<Card> others = new ArrayList<>();

        for (Card c : player.getCardsIn(ZoneType.Battlefield)) {
            if (c.isLand()) {
                lands.add(c);
            } else if (c.isCreature()) {
                creatures.add(c);
            } else {
                others.add(c);
            }
        }

        System.out.println(marker + "  Lands in play: " + lands.size());
        if (!lands.isEmpty()) {
            for (Card land : lands) {
                System.out.println(marker + "    - " + land.getName() + (land.isTapped() ? " (tapped)" : ""));
            }
        }

        System.out.println(marker + "  Creatures: " + creatures.size());
        if (!creatures.isEmpty()) {
            for (Card creature : creatures) {
                System.out.println(marker + "    - " + creature.getName() +
                    " (" + creature.getNetPower() + "/" + creature.getNetToughness() + ")" +
                    (creature.isTapped() ? " (tapped)" : ""));
            }
        }

        if (!others.isEmpty()) {
            System.out.println(marker + "  Other permanents: " + others.size());
            for (Card other : others) {
                System.out.println(marker + "    - " + other.getName());
            }
        }
    }

    /**
     * Get user input for a choice.
     */
    public static int getUserChoice(String prompt, List<String> options) {
        System.out.println();
        System.out.println(prompt);
        for (int i = 0; i < options.size(); i++) {
            System.out.println("  " + (i + 1) + ". " + options.get(i));
        }
        System.out.print("Enter choice (1-" + options.size() + "): ");

        try {
            String line = reader.readLine();
            if (line == null || line.trim().isEmpty()) {
                return 0; // Default to first option
            }
            int choice = Integer.parseInt(line.trim());
            if (choice >= 1 && choice <= options.size()) {
                return choice - 1;
            }
        } catch (IOException e) {
            System.err.println("Error reading input: " + e.getMessage());
        } catch (NumberFormatException e) {
            System.err.println("Invalid input. Please enter a number.");
        }

        return 0; // Default to first option on error
    }

    /**
     * Confirm an action with the user.
     */
    public static boolean confirm(String message) {
        System.out.println();
        System.out.print(message + " (y/n): ");

        try {
            String line = reader.readLine();
            if (line == null) {
                return false;
            }
            return line.trim().toLowerCase().startsWith("y");
        } catch (IOException e) {
            System.err.println("Error reading input: " + e.getMessage());
            return false;
        }
    }
}
