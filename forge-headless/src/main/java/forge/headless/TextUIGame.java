package forge.headless;

import forge.LobbyPlayer;
import forge.deck.Deck;
import forge.deck.io.DeckSerializer;
import forge.game.*;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.zone.ZoneType;
import forge.gamemodes.puzzle.PuzzleIO;
import forge.item.PaperCard;
import forge.localinstance.properties.ForgeConstants;
import forge.model.FModel;
import forge.player.GamePlayerUtil;
import forge.util.FileUtil;
import forge.util.MyRandom;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Text UI game mode for Forge.
 * Allows interactive gameplay through a text-based interface.
 */
public class TextUIGame {

    public static void run(String[] args) {
        // Check for help flag BEFORE loading cards
        if (args.length < 2 || (args.length >= 2 && "--help".equals(args[1]))) {
            System.out.println("=== Forge Text UI Mode ===");
            showHelp();
            return;
        }

        System.out.println("=== Forge Text UI Mode ===");

        // Load cards asynchronously on a background thread
        System.out.println("Loading card database in background...");
        Thread cardLoadingThread = new Thread(() -> {
            FModel.initialize(null, null);
        }, "CardLoadingThread");
        cardLoadingThread.start();

        // Install TUI GUI base which intercepts game log messages
        TUIGuiBase.install();

        // Wait for card loading to complete
        try {
            cardLoadingThread.join();
        } catch (InterruptedException e) {
            System.err.println("Card loading interrupted: " + e.getMessage());
            Thread.currentThread().interrupt();
            return;
        }
        System.out.println("Card database loaded successfully.");

        if (args.length < 3) {
            showHelp();
            return;
        }

        // Parse deck arguments
        String humanDeckName = args[1];
        String aiDeckName = args[2];

        // Parse optional flags
        GameType type = GameType.Constructed;
        AgentType player1Agent = AgentType.TUI;  // Default: interactive TUI for player 1
        AgentType player2Agent = AgentType.AI;   // Default: AI for player 2
        boolean askMana = false;  // Default: don't prompt for mana abilities
        boolean numericChoices = false;  // Default: use text-based prompts
        Long seed = null;  // Random seed for deterministic testing
        String startStatePath = null;  // Path to .pzl file for loading game state

        // Look for flags after the deck names
        for (int i = 3; i < args.length; i++) {
            String arg = args[i];

            // Handle both "--flag value" and "--flag=value" formats
            String flagName = arg;
            String flagValue = null;
            if (arg.contains("=")) {
                String[] parts = arg.split("=", 2);
                flagName = parts[0];
                flagValue = parts.length > 1 ? parts[1] : "";
            }

            if ("-f".equals(flagName)) {
                if (flagValue != null) {
                    type = GameType.valueOf(flagValue);
                } else if (i + 1 < args.length) {
                    type = GameType.valueOf(args[i + 1]);
                    i++; // Skip the format argument
                }
            } else if ("--p1".equals(flagName) || "--p1-agent".equals(flagName)) {
                String agentValue = flagValue != null ? flagValue : (i + 1 < args.length ? args[++i] : null);
                if (agentValue == null) {
                    System.err.println("Missing value for --p1");
                    System.err.println("Valid options: tui, ai, random, zero");
                    return;
                }
                try {
                    player1Agent = AgentType.valueOf(agentValue.toUpperCase());
                } catch (IllegalArgumentException e) {
                    System.err.println("Invalid agent type for --p1: " + agentValue);
                    System.err.println("Valid options: tui, ai, random, zero");
                    return;
                }
            } else if ("--p2".equals(flagName) || "--p2-agent".equals(flagName)) {
                String agentValue = flagValue != null ? flagValue : (i + 1 < args.length ? args[++i] : null);
                if (agentValue == null) {
                    System.err.println("Missing value for --p2");
                    System.err.println("Valid options: tui, ai, random, zero");
                    return;
                }
                try {
                    player2Agent = AgentType.valueOf(agentValue.toUpperCase());
                } catch (IllegalArgumentException e) {
                    System.err.println("Invalid agent type for --p2: " + agentValue);
                    System.err.println("Valid options: tui, ai, random, zero");
                    return;
                }
            } else if ("--player2-tui".equals(flagName)) {
                // Legacy flag support - convert to new agent system
                System.out.println("Warning: --player2-tui is deprecated, use --p2=tui instead");
                player2Agent = AgentType.TUI;
            } else if ("--askmana".equals(flagName)) {
                if (flagValue != null) {
                    askMana = "true".equalsIgnoreCase(flagValue);
                } else if (i + 1 < args.length) {
                    askMana = "true".equalsIgnoreCase(args[i + 1]);
                    i++; // Skip the boolean argument
                } else {
                    askMana = true; // If no argument, default to true
                }
            } else if ("--numeric-choices".equals(flagName)) {
                numericChoices = true;
            } else if ("--seed".equals(flagName)) {
                String seedValue = flagValue != null ? flagValue : (i + 1 < args.length ? args[++i] : null);
                if (seedValue == null) {
                    System.err.println("Missing value for --seed");
                    return;
                }
                try {
                    seed = Long.parseLong(seedValue);
                } catch (NumberFormatException e) {
                    System.err.println("Invalid seed value: " + seedValue);
                    return;
                }
            } else if ("--start-state".equals(flagName)) {
                String pathValue = flagValue != null ? flagValue : (i + 1 < args.length ? args[++i] : null);
                if (pathValue == null) {
                    System.err.println("Missing value for --start-state");
                    return;
                }
                startStatePath = pathValue;
            } else {
                // Unrecognized argument - warn the user
                System.err.println("Warning: Unrecognized argument: " + arg);
                System.err.println("Run with --help to see valid options");
            }
        }

        // Set random seed if provided (must be done BEFORE creating the game)
        if (seed != null) {
            System.out.println("Setting random seed: " + seed);
            MyRandom.setRandom(new Random(seed));
        }

        // Load decks
        Deck humanDeck = loadDeck(humanDeckName, type);
        Deck aiDeck = loadDeck(aiDeckName, type);

        if (humanDeck == null || aiDeck == null) {
            System.out.println("Failed to load decks. Exiting.");
            return;
        }

        System.out.println("Starting game: Player 1 (" + humanDeck.getName() + ", " + player1Agent + ") vs Player 2 (" + aiDeck.getName() + ", " + player2Agent + ")");
        System.out.println();

        // Create players
        List<RegisteredPlayer> players = new ArrayList<>();

        // Player 1
        RegisteredPlayer player1;
        if (type.equals(GameType.Commander)) {
            player1 = RegisteredPlayer.forCommander(humanDeck);
        } else {
            player1 = new RegisteredPlayer(humanDeck);
        }
        // Create LobbyPlayer based on agent type
        LobbyPlayer player1Lobby;
        if (player1Agent == AgentType.AI) {
            player1Lobby = GamePlayerUtil.createAiPlayer("AI-" + humanDeck.getName(), 0);
        } else {
            player1Lobby = GamePlayerUtil.getGuiPlayer("Player 1", 0, 0, false);
        }
        player1.setPlayer(player1Lobby);
        players.add(player1);

        // Player 2
        RegisteredPlayer player2;
        if (type.equals(GameType.Commander)) {
            player2 = RegisteredPlayer.forCommander(aiDeck);
        } else {
            player2 = new RegisteredPlayer(aiDeck);
        }
        // Create LobbyPlayer based on agent type
        LobbyPlayer player2Lobby;
        if (player2Agent == AgentType.AI) {
            player2Lobby = GamePlayerUtil.createAiPlayer("AI-" + aiDeck.getName(), 0);
        } else {
            player2Lobby = GamePlayerUtil.getGuiPlayer("Player 2", 1, 1, false);
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

        // Install controller for player 1 based on agent type
        if (player1GamePlayer != null && player1Agent != AgentType.AI) {
            installController(player1GamePlayer, player1Lobby, game, player1Agent, askMana, numericChoices);
        }

        // Install controller for player 2 based on agent type
        if (player2GamePlayer != null && player2Agent != AgentType.AI) {
            installController(player2GamePlayer, player2Lobby, game, player2Agent, askMana, numericChoices);
        }

        // Set the current game for log monitoring
        TUIGuiBase.setCurrentGame(game);

        // Load game state from .pzl file if provided
        if (startStatePath != null) {
            System.out.println("Loading game state from: " + startStatePath);
            if (!loadGameState(game, startStatePath)) {
                System.err.println("Failed to load game state. Exiting.");
                return;
            }
        }

        // Start the game
        System.out.println("Game starting...");
        System.out.println("=".repeat(60));

        if (startStatePath != null) {
            // Game state was loaded, so the game is already set up
            // Just run the game loop
            game.getAction().invoke(() -> {
                game.getPhaseHandler().devModeSet(game.getPhaseHandler().getPhase(),
                                                   game.getPhaseHandler().getPlayerTurn(),
                                                   game.getPhaseHandler().getTurn());
            });
        } else {
            // Normal game start
            match.startGame(game);
        }

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

        if (player2GamePlayer != null && player2GamePlayer.getController() instanceof PlayerControllerTUI) {
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
        System.out.println("       forge-headless tui --help");
        System.out.println();
        System.out.println("Arguments:");
        System.out.println("  player1_deck  - Deck file (.dck) or deck name for player 1");
        System.out.println("  player2_deck  - Deck file (.dck) or deck name for player 2");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --help                 - Show this help message");
        System.out.println("  -f <format>            - Game format (default: Constructed)");
        System.out.println("  --p1 <type>            - Player 1 agent type (default: tui)");
        System.out.println("  --p2 <type>            - Player 2 agent type (default: ai)");
        System.out.println("                           Agent types: tui, ai, random, zero");
        System.out.println("                           - tui: Interactive via stdin");
        System.out.println("                           - ai: Forge built-in AI");
        System.out.println("                           - random: Random valid choices");
        System.out.println("                           - zero: Always pass (choose 0)");
        System.out.println("  --askmana [true/false] - Prompt for mana abilities (default: false)");
        System.out.println("  --numeric-choices      - Use numeric-only input (no text commands)");
        System.out.println("  --seed <long>          - Set random seed for deterministic testing");
        System.out.println("  --start-state <file>   - Load game state from .pzl file");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  forge-headless tui --help");
        System.out.println("  forge-headless tui a.dck b.dck");
        System.out.println("  forge-headless tui deck1.dck deck2.dck --p1=ai --p2=ai");
        System.out.println("  forge-headless tui deck1.dck deck2.dck --p2=tui");
        System.out.println("  forge-headless tui deck1.dck deck2.dck --p1=random --seed 12345");
        System.out.println("  forge-headless tui deck1.dck deck2.dck --start-state puzzle.pzl");
        System.out.println();
        System.out.println("During gameplay, you will be prompted with options:");
        System.out.println("  0. Pass priority (do nothing)");
        System.out.println("  1-N. Play lands, cast spells, etc.");
        System.out.println();
        System.out.println("Interactive commands during gameplay:");
        System.out.println("  ?  - Show help");
        System.out.println("  v  - View cards in detail");
        System.out.println("  g  - View graveyards");
        System.out.println("  b  - View battlefield/game state");
        System.out.println("  s  - View current stack");
    }

    /**
     * Helper method to install a controller for a player using reflection.
     */
    private static void installController(Player gamePlayer, LobbyPlayer lobbyPlayer, Game game,
                                          AgentType agentType, boolean askMana, boolean numericChoices) {
        try {
            forge.game.player.PlayerController controller;

            switch (agentType) {
                case TUI:
                    controller = new PlayerControllerTUI(game, gamePlayer, lobbyPlayer, askMana, numericChoices);
                    break;
                case ZERO:
                    controller = new PlayerControllerZero(game, gamePlayer, lobbyPlayer, askMana, numericChoices);
                    break;
                case RANDOM:
                    controller = new PlayerControllerRandom(game, gamePlayer, lobbyPlayer, askMana, numericChoices);
                    break;
                case AI:
                    // AI controller is already installed, no need to replace
                    return;
                default:
                    System.err.println("Unknown agent type: " + agentType);
                    return;
            }

            java.lang.reflect.Field controllerField = Player.class.getDeclaredField("controller");
            controllerField.setAccessible(true);
            controllerField.set(gamePlayer, controller);
            System.out.println(agentType + " Controller installed for player: " + gamePlayer.getName());
        } catch (Exception e) {
            System.err.println("Failed to install controller: " + e.getMessage());
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

    /**
     * Loads a game state from a .pzl file and applies it to the game.
     * This is a simplified implementation that directly manipulates zones.
     *
     * @param game The game to apply the state to
     * @param puzzleFilePath Path to the .pzl file
     * @return true if successful, false otherwise
     */
    private static boolean loadGameState(Game game, String puzzleFilePath) {
        try {
            File puzzleFile = new File(puzzleFilePath);
            if (!puzzleFile.exists()) {
                System.err.println("Puzzle file not found: " + puzzleFilePath);
                return false;
            }

            // Read and parse the puzzle file
            List<String> pfData = FileUtil.readFile(puzzleFilePath);
            Map<String, List<String>> puzzleSections = PuzzleIO.parsePuzzleSections(pfData);

            // Get the [state] section
            List<String> stateLines = puzzleSections.get("state");
            if (stateLines == null || stateLines.isEmpty()) {
                System.err.println("No [state] section found in puzzle file");
                return false;
            }

            // Parse state into a map
            Map<String, String> stateMap = new java.util.HashMap<>();
            for (String line : stateLines) {
                if (line.contains("=")) {
                    String[] parts = line.split("=", 2);
                    stateMap.put(parts[0].trim(), parts[1].trim());
                }
            }

            // Apply state to game within the game's action thread
            final Map<String, String> finalStateMap = stateMap;
            game.getAction().invoke(() -> applyPuzzleState(game, finalStateMap));

            System.out.println("Game state loaded successfully from: " + puzzleFilePath);
            return true;
        } catch (Exception e) {
            System.err.println("Error loading game state from puzzle file: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Applies the parsed puzzle state to the game.
     * Must be called within game.getAction().invoke()
     */
    private static void applyPuzzleState(Game game, Map<String, String> state) {
        try {
            List<Player> players = game.getPlayers();
            if (players.size() < 2) {
                throw new RuntimeException("Game must have at least 2 players");
            }

            Player humanPlayer = players.get(0);
            Player aiPlayer = players.get(1);

            // Set life totals
            if (state.containsKey("humanlife")) {
                humanPlayer.setLife(Integer.parseInt(state.get("humanlife")), null);
            }
            if (state.containsKey("ailife")) {
                aiPlayer.setLife(Integer.parseInt(state.get("ailife")), null);
            }

            // Clear all zones first by removing cards one by one
            clearZone(game, humanPlayer, ZoneType.Hand);
            clearZone(game, humanPlayer, ZoneType.Library);
            clearZone(game, humanPlayer, ZoneType.Battlefield);
            clearZone(game, humanPlayer, ZoneType.Graveyard);

            clearZone(game, aiPlayer, ZoneType.Hand);
            clearZone(game, aiPlayer, ZoneType.Library);
            clearZone(game, aiPlayer, ZoneType.Battlefield);
            clearZone(game, aiPlayer, ZoneType.Graveyard);

            // Add cards to zones
            addCardsToZone(game, humanPlayer, ZoneType.Hand, state.get("humanhand"));
            addCardsToZone(game, humanPlayer, ZoneType.Library, state.get("humanlibrary"));
            addCardsToZone(game, humanPlayer, ZoneType.Battlefield, state.get("humanbattlefield"));
            addCardsToZone(game, humanPlayer, ZoneType.Graveyard, state.get("humangraveyard"));

            addCardsToZone(game, aiPlayer, ZoneType.Hand, state.get("aihand"));
            addCardsToZone(game, aiPlayer, ZoneType.Library, state.get("ailibrary"));
            addCardsToZone(game, aiPlayer, ZoneType.Battlefield, state.get("aibattlefield"));
            addCardsToZone(game, aiPlayer, ZoneType.Graveyard, state.get("aigraveyard"));

            // Set turn and phase
            int turn = state.containsKey("turn") ? Integer.parseInt(state.get("turn")) : 1;
            String activePlayerStr = state.get("activeplayer");
            Player activePlayer = "ai".equalsIgnoreCase(activePlayerStr) ? aiPlayer : humanPlayer;

            String phaseStr = state.get("activephase");
            PhaseType phase = phaseStr != null ? PhaseType.smartValueOf(phaseStr) : PhaseType.MAIN1;

            game.getPhaseHandler().devModeSet(phase, activePlayer, turn);

            System.out.println("Puzzle state applied: Turn " + turn + ", " + activePlayer.getName() + "'s " + phase);
        } catch (Exception e) {
            System.err.println("Error applying puzzle state: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Clears all cards from a zone
     */
    private static void clearZone(Game game, Player player, ZoneType zone) {
        // Make a copy of the card list to avoid concurrent modification
        List<Card> cards = new ArrayList<Card>();
        for (Card card : player.getZone(zone).getCards()) {
            cards.add(card);
        }
        for (Card card : cards) {
            game.getAction().exile(card, null, null);
        }
    }

    /**
     * Adds cards to a zone from a semicolon-separated string
     */
    private static void addCardsToZone(Game game, Player player, ZoneType zone, String cardListStr) {
        if (cardListStr == null || cardListStr.trim().isEmpty()) {
            return;
        }

        String[] cardNames = cardListStr.split(";");
        for (String cardName : cardNames) {
            cardName = cardName.trim();
            if (!cardName.isEmpty()) {
                try {
                    // Create a card from the name
                    PaperCard paperCard = FModel.getMagicDb().getCommonCards().getCard(cardName);
                    if (paperCard == null) {
                        System.err.println("Warning: Card not found: " + cardName);
                        continue;
                    }

                    Card card = Card.fromPaperCard(paperCard, player);

                    // Add to the appropriate zone
                    player.getZone(zone).add(card);
                } catch (Exception e) {
                    System.err.println("Error adding card " + cardName + " to " + zone + ": " + e.getMessage());
                }
            }
        }
    }
}
