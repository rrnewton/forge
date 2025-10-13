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

        GameType type = GameType.Constructed;
        if (args.length >= 4 && "-f".equals(args[3])) {
            if (args.length >= 5) {
                type = GameType.valueOf(args[4]);
            }
        }

        // Load decks
        Deck humanDeck = loadDeck(humanDeckName, type);
        Deck aiDeck = loadDeck(aiDeckName, type);

        if (humanDeck == null || aiDeck == null) {
            System.out.println("Failed to load decks. Exiting.");
            return;
        }

        System.out.println("Starting game: Human (" + humanDeck.getName() + ") vs AI (" + aiDeck.getName() + ")");
        System.out.println();

        // Create players
        List<RegisteredPlayer> players = new ArrayList<>();

        // Human player with TUI controller
        RegisteredPlayer humanPlayer;
        if (type.equals(GameType.Commander)) {
            humanPlayer = RegisteredPlayer.forCommander(humanDeck);
        } else {
            humanPlayer = new RegisteredPlayer(humanDeck);
        }
        LobbyPlayer humanLobby = GamePlayerUtil.getGuiPlayer();
        humanPlayer.setPlayer(humanLobby);
        players.add(humanPlayer);

        // AI player
        RegisteredPlayer aiPlayer;
        if (type.equals(GameType.Commander)) {
            aiPlayer = RegisteredPlayer.forCommander(aiDeck);
        } else {
            aiPlayer = new RegisteredPlayer(aiDeck);
        }
        String aiName = TextUtil.concatNoSpace("AI-", aiDeck.getName());
        aiPlayer.setPlayer(GamePlayerUtil.createAiPlayer(aiName, 0));
        players.add(aiPlayer);

        // Create and start the match
        GameRules rules = new GameRules(type);
        Match match = new Match(rules, players, "TUI Game");

        Game game = match.createGame();

        // Replace the human player's controller with our TUI controller
        // We need to do this after game creation but BEFORE startGame
        // because startGame calls prepareAllZones which may call controller methods
        Player humanGamePlayer = null;
        for (Player p : game.getPlayers()) {
            if (p.getLobbyPlayer() == humanLobby) {
                humanGamePlayer = p;
                System.out.println("Found human player: " + p.getName());
                break;
            }
        }

        if (humanGamePlayer != null) {
            // Create the TUI controller
            PlayerControllerTUI tuiController = new PlayerControllerTUI(game, humanGamePlayer, humanLobby);

            // Use reflection to set the controller field since there's no public setter
            try {
                java.lang.reflect.Field controllerField = Player.class.getDeclaredField("controller");
                controllerField.setAccessible(true);
                controllerField.set(humanGamePlayer, tuiController);
                System.out.println("TUI Controller installed for player: " + humanGamePlayer.getName());
            } catch (Exception e) {
                System.err.println("Failed to install TUI controller: " + e.getMessage());
                e.printStackTrace();
                return;
            }
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
        if (humanGamePlayer != null && humanGamePlayer.getController() instanceof PlayerControllerTUI) {
            PlayerControllerTUI tuiController = (PlayerControllerTUI) humanGamePlayer.getController();
            System.out.println();
            System.out.println("=== Choice Statistics ===");
            System.out.println("Total choices made: " + tuiController.getTotalChoicesMade());
            System.out.println("Total options presented: " + tuiController.getTotalChoiceOptions());
            if (tuiController.getTotalChoicesMade() > 0) {
                double avgOptions = (double) tuiController.getTotalChoiceOptions() / tuiController.getTotalChoicesMade();
                System.out.printf("Average options per choice: %.2f%n", avgOptions);
            }
        }
    }

    private static void showHelp() {
        System.out.println("Text UI Mode - Interactive Forge Gameplay");
        System.out.println();
        System.out.println("Usage: forge-headless tui <human_deck> <ai_deck> [-f <format>]");
        System.out.println();
        System.out.println("Arguments:");
        System.out.println("  human_deck - Deck file (.dck) or deck name for human player");
        System.out.println("  ai_deck    - Deck file (.dck) or deck name for AI player");
        System.out.println("  -f format  - Game format (default: Constructed)");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  forge-headless tui a.dck a.dck");
        System.out.println("  forge-headless tui MyDeck AIDeck -f Constructed");
        System.out.println();
        System.out.println("During gameplay, you will be prompted with options:");
        System.out.println("  0. Pass priority (do nothing)");
        System.out.println("  1-N. Play a land from your hand");
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
