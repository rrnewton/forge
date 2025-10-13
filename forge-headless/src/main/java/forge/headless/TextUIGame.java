package forge.headless;

import forge.LobbyPlayer;
import forge.deck.Deck;
import forge.deck.io.DeckSerializer;
import forge.game.*;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.localinstance.properties.ForgeConstants;
import forge.model.FModel;
import forge.player.GamePlayerUtil;
import forge.util.TextUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Text UI game mode for Forge.
 * Allows interactive gameplay through a text-based interface.
 */
public class TextUIGame {

    public static void run(String[] args) {
        FModel.initialize(null, null);

        // Install TUI GUI base which intercepts game log messages
        TUIGuiBase.install();

        System.out.println("=== Forge Text UI Mode ===");

        if (args.length < 3) {
            showHelp();
            return;
        }

        // Parse deck arguments
        String humanDeckName = args[1];
        String aiDeckName = args[2];

        // Parse optional flags
        GameType type = GameType.Constructed;
        boolean player2IsTUI = false;

        // Look for flags after the deck names
        for (int i = 3; i < args.length; i++) {
            if ("-f".equals(args[i]) && i + 1 < args.length) {
                type = GameType.valueOf(args[i + 1]);
                i++; // Skip the format argument
            } else if ("-p2".equals(args[i]) || "--player2-tui".equals(args[i])) {
                player2IsTUI = true;
            }
        }

        // Load decks
        Deck humanDeck = loadDeck(humanDeckName, type);
        Deck aiDeck = loadDeck(aiDeckName, type);

        if (humanDeck == null || aiDeck == null) {
            System.out.println("Failed to load decks. Exiting.");
            return;
        }

        String player2Type = player2IsTUI ? "TUI Player 2" : "AI";
        System.out.println("Starting game: Player 1 (" + humanDeck.getName() + ") vs " + player2Type + " (" + aiDeck.getName() + ")");
        System.out.println();

        // Create players
        List<RegisteredPlayer> players = new ArrayList<>();

        // Player 1 with TUI controller
        RegisteredPlayer player1;
        if (type.equals(GameType.Commander)) {
            player1 = RegisteredPlayer.forCommander(humanDeck);
        } else {
            player1 = new RegisteredPlayer(humanDeck);
        }
        LobbyPlayer player1Lobby = GamePlayerUtil.getGuiPlayer();
        player1.setPlayer(player1Lobby);
        players.add(player1);

        // Player 2 (TUI or AI depending on flag)
        RegisteredPlayer player2;
        LobbyPlayer player2Lobby;
        if (type.equals(GameType.Commander)) {
            player2 = RegisteredPlayer.forCommander(aiDeck);
        } else {
            player2 = new RegisteredPlayer(aiDeck);
        }
        if (player2IsTUI) {
            // Create second GUI player for TUI
            // Note: Both players will share the same LobbyPlayer instance but get different controllers
            player2Lobby = GamePlayerUtil.getGuiPlayer();
        } else {
            // Create AI player
            String aiName = TextUtil.concatNoSpace("AI-", aiDeck.getName());
            player2Lobby = GamePlayerUtil.createAiPlayer(aiName, 0);
        }
        player2.setPlayer(player2Lobby);
        players.add(player2);

        // Create and start the match
        GameRules rules = new GameRules(type);
        Match match = new Match(rules, players, "TUI Game");

        Game game = match.createGame();

        // Replace player controllers with TUI controllers
        // We need to do this after game creation but BEFORE startGame
        // because startGame calls prepareAllZones which may call controller methods
        Player player1GamePlayer = null;
        Player player2GamePlayer = null;

        for (Player p : game.getPlayers()) {
            if (p.getLobbyPlayer() == player1Lobby) {
                player1GamePlayer = p;
                System.out.println("Found player 1: " + p.getName());
            } else if (p.getLobbyPlayer() == player2Lobby) {
                player2GamePlayer = p;
                System.out.println("Found player 2: " + p.getName());
            }
        }

        // Install TUI controller for player 1
        if (player1GamePlayer != null) {
            PlayerControllerTUI tuiController1 = new PlayerControllerTUI(game, player1GamePlayer, player1Lobby);
            installTUIController(player1GamePlayer, tuiController1);
        }

        // Install TUI controller for player 2 if requested
        if (player2IsTUI && player2GamePlayer != null) {
            PlayerControllerTUI tuiController2 = new PlayerControllerTUI(game, player2GamePlayer, player2Lobby);
            installTUIController(player2GamePlayer, tuiController2);
        }

        // Set the current game for log monitoring
        TUIGuiBase.setCurrentGame(game);

        // Start the game
        System.out.println("Game starting...");
        System.out.println("=".repeat(60));
        match.startGame(game);

        // Game is over
        System.out.println();
        System.out.println("=".repeat(60));
        System.out.println("GAME OVER");

        if (game.getOutcome().isDraw()) {
            System.out.println("Result: Draw!");
        } else {
            System.out.println("Winner: " + game.getOutcome().getWinningLobbyPlayer().getName());
        }

        // Print choice statistics if available
        System.out.println();
        System.out.println("=== Choice Statistics ===");

        if (player1GamePlayer != null && player1GamePlayer.getController() instanceof PlayerControllerTUI) {
            PlayerControllerTUI tuiController1 = (PlayerControllerTUI) player1GamePlayer.getController();
            System.out.println("Player 1 (" + player1GamePlayer.getName() + "):");
            System.out.println("  Total choices made: " + tuiController1.getTotalChoicesMade());
            System.out.println("  Total options presented: " + tuiController1.getTotalChoiceOptions());
            if (tuiController1.getTotalChoicesMade() > 0) {
                double avgOptions = (double) tuiController1.getTotalChoiceOptions() / tuiController1.getTotalChoicesMade();
                System.out.printf("  Average options per choice: %.2f%n", avgOptions);
            }
        }

        if (player2IsTUI && player2GamePlayer != null && player2GamePlayer.getController() instanceof PlayerControllerTUI) {
            PlayerControllerTUI tuiController2 = (PlayerControllerTUI) player2GamePlayer.getController();
            System.out.println("Player 2 (" + player2GamePlayer.getName() + "):");
            System.out.println("  Total choices made: " + tuiController2.getTotalChoicesMade());
            System.out.println("  Total options presented: " + tuiController2.getTotalChoiceOptions());
            if (tuiController2.getTotalChoicesMade() > 0) {
                double avgOptions = (double) tuiController2.getTotalChoiceOptions() / tuiController2.getTotalChoicesMade();
                System.out.printf("  Average options per choice: %.2f%n", avgOptions);
            }
        }
    }

    private static void showHelp() {
        System.out.println("Text UI Mode - Interactive Forge Gameplay");
        System.out.println();
        System.out.println("Usage: forge-headless tui <player1_deck> <player2_deck> [options]");
        System.out.println();
        System.out.println("Arguments:");
        System.out.println("  player1_deck  - Deck file (.dck) or deck name for player 1");
        System.out.println("  player2_deck  - Deck file (.dck) or deck name for player 2");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  -f <format>           - Game format (default: Constructed)");
        System.out.println("  -p2, --player2-tui    - Enable TUI control for player 2 (default: AI)");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  forge-headless tui a.dck b.dck");
        System.out.println("  forge-headless tui MyDeck AIDeck -f Constructed");
        System.out.println("  forge-headless tui deck1.dck deck2.dck -p2");
        System.out.println();
        System.out.println("During gameplay, you will be prompted with options:");
        System.out.println("  0. Pass priority (do nothing)");
        System.out.println("  1-N. Play lands, cast spells, etc.");
    }

    /**
     * Helper method to install a TUI controller for a player using reflection.
     */
    private static void installTUIController(Player gamePlayer, PlayerControllerTUI controller) {
        try {
            java.lang.reflect.Field controllerField = Player.class.getDeclaredField("controller");
            controllerField.setAccessible(true);
            controllerField.set(gamePlayer, controller);
            System.out.println("TUI Controller installed for player: " + gamePlayer.getName());
        } catch (Exception e) {
            System.err.println("Failed to install TUI controller: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static Deck loadDeck(String deckName, GameType type) {
        int dotPos = deckName.lastIndexOf('.');
        if (dotPos > 0 && dotPos == deckName.length() - 4) {
            // It's a file - first check if it's an absolute or relative path
            File f = new File(deckName);

            // If not found as-is, try with the base directory
            if (!f.exists()) {
                String baseDir = type.equals(GameType.Commander) ?
                        ForgeConstants.DECK_COMMANDER_DIR : ForgeConstants.DECK_CONSTRUCTED_DIR;
                f = new File(baseDir + deckName);
            }

            if (!f.exists()) {
                System.out.println("Deck file not found: " + deckName);
                return null;
            }

            return DeckSerializer.fromFile(f);
        }

        // It's a deck name
        if (type.equals(GameType.Commander)) {
            return FModel.getDecks().getCommander().get(deckName);
        } else {
            return FModel.getDecks().getConstructed().get(deckName);
        }
    }
}
