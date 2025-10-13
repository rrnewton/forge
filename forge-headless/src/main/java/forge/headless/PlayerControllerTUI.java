package forge.headless;

import com.google.common.collect.Multimap;
import forge.LobbyPlayer;
import forge.ai.ComputerUtilMana;
import forge.game.GameEntity;
import forge.ai.PlayerControllerAi;
import forge.deck.DeckSection;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.card.CardCollectionView;
import forge.game.combat.Combat;
import forge.game.combat.CombatUtil;
import forge.game.phase.PhaseHandler;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import forge.item.PaperCard;
import forge.util.collect.FCollectionView;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * TUI Player Controller - Extends AI controller but overrides key methods
 * to provide interactive text-based gameplay.
 *
 * For now, this only supports two actions:
 * 1. Pass priority (do nothing)
 * 2. Play a land from hand
 */
public class PlayerControllerTUI extends PlayerControllerAi {

    private final BufferedReader reader;

    // Choice tracking statistics
    private int totalChoicesMade = 0;
    private int totalChoiceOptions = 0;

    public PlayerControllerTUI(Game game, Player p, LobbyPlayer lp) {
        this(game, p, lp, new BufferedReader(new InputStreamReader(System.in)));
    }

    /**
     * Constructor with injectable reader for testing.
     */
    public PlayerControllerTUI(Game game, Player p, LobbyPlayer lp, BufferedReader reader) {
        super(game, p, lp);
        this.reader = reader;
    }

    /**
     * Get statistics about choices made during the game.
     */
    public int getTotalChoicesMade() {
        return totalChoicesMade;
    }

    public int getTotalChoiceOptions() {
        return totalChoiceOptions;
    }

    @Override
    public boolean isAI() {
        return false; // We're a human player
    }

    @Override
    public void revealAnte(String message, Multimap<Player, PaperCard> removedAnteCards) {
        // No-op for TUI - we don't need to show ante cards
        System.out.println("[Ante] " + message);
    }

    @Override
    public void revealAISkipCards(String message, Map<Player, Map<DeckSection, List<? extends PaperCard>>> deckCards) {
        // No-op for TUI - we don't need to show AI-unplayable cards
        // This is called during game setup to show cards the AI can't use
    }

    @Override
    public void revealUnsupported(Map<Player, List<PaperCard>> unsupported) {
        // No-op for TUI
    }

    @Override
    public boolean chooseTargetsFor(SpellAbility sa) {
        // For now, use AI targeting but print what was chosen
        // This lets the AI handle complex targeting logic while we show the user what happened
        boolean result = super.chooseTargetsFor(sa);

        if (result && sa.usesTargeting() && sa.getTargets() != null && !sa.getTargets().isEmpty()) {
            System.out.println("\n>> Auto-targeted with " + sa.getHostCard().getName() + ":");
            for (forge.game.GameObject target : sa.getTargets()) {
                String desc = target.toString();
                if (target instanceof Player) {
                    Player p = (Player) target;
                    desc = p.getName() + " (Life: " + p.getLife() + ")";
                } else if (target instanceof Card) {
                    Card c = (Card) target;
                    desc = c.getName();
                    if (c.isCreature()) {
                        desc += " (" + c.getNetPower() + "/" + c.getNetToughness() + ")";
                    }
                }
                System.out.println("   * " + desc);
            }
            System.out.println();
        }

        return result;
    }

    @Override
    public List<SpellAbility> chooseSpellAbilityToPlay() {
        // Print any new game log entries
        TUIGuiBase.printNewLogEntries();

        Game game = getGame();
        PhaseHandler ph = game.getPhaseHandler();
        boolean isMyTurn = ph.isPlayerTurn(player);
        boolean isMainPhase = ph.is(forge.game.phase.PhaseType.MAIN1) || ph.is(forge.game.phase.PhaseType.MAIN2);
        boolean isPostCombatMain = ph.is(forge.game.phase.PhaseType.MAIN2);
        boolean isEndStep = ph.is(forge.game.phase.PhaseType.END_OF_TURN);
        boolean stackHasItems = !game.getStack().isEmpty();

        // Get playable actions from hand
        List<SpellAbility> landAbilities = getPlayableLands();
        List<SpellAbility> creatureAbilities = new ArrayList<>();
        List<SpellAbility> artifactAbilities = new ArrayList<>();
        List<SpellAbility> instantAbilities = new ArrayList<>();
        List<SpellAbility> sorceryAbilities = new ArrayList<>();

        // In any main phase, check for castable sorcery-speed spells
        if (isMainPhase) {
            creatureAbilities = getCastableCreaturesAndArtifacts(true);
            artifactAbilities = getCastableCreaturesAndArtifacts(false);
            sorceryAbilities = getCastableSorceries();
        }

        // Instants can be cast at any time we have priority
        instantAbilities = getCastableInstants();

        // Get activated abilities from permanents on the battlefield
        List<SpellAbility> activatedAbilities = getActivatedAbilities();

        // Count total available actions
        int totalActions = landAbilities.size() + creatureAbilities.size() + artifactAbilities.size() +
                           instantAbilities.size() + sorceryAbilities.size() + activatedAbilities.size();

        // Smart auto-passing on opponent's turn
        if (!isMyTurn) {
            // Only prompt on opponent's turn if:
            // 1. Stack has items (we might want to respond), OR
            // 2. It's end step AND we have instants (classic "at end of your turn" timing), OR
            // 3. We have activated abilities

            boolean hasInstantSpeedActions = !instantAbilities.isEmpty() || !activatedAbilities.isEmpty();
            boolean shouldPrompt = stackHasItems || (isEndStep && hasInstantSpeedActions);

            if (!shouldPrompt) {
                // Silently pass - don't spam output during opponent's turn
                return null;
            }

            // If no actions available even though we should prompt, auto-pass with minimal output
            if (totalActions == 0) {
                return null;
            }
        } else {
            // On our turn, if there are no options besides passing, auto-pass without prompting
            if (totalActions == 0) {
                System.out.println(">> Auto-passing priority (no actions available)...\n");
                return null;
            }
        }

        // Only display game state when we're prompting the user for input
        displayGameState();

        // Show options to user with context-appropriate header
        if (!isMyTurn) {
            System.out.println("\n=== OPPONENT'S TURN ===");
            if (stackHasItems) {
                System.out.println("[Respond to spell on stack]");
            } else if (isEndStep) {
                System.out.println("[End of opponent's turn]");
            }
        } else {
            System.out.println("\n=== YOUR TURN ===");
            if (isPostCombatMain) {
                System.out.println("[Post-Combat Main Phase]");
            } else if (isMainPhase) {
                System.out.println("[Pre-Combat Main Phase]");
            }
        }

        System.out.println("What would you like to do?");
        System.out.println("  0. Pass priority (do nothing)");

        int optionNum = 1;

        // Show land options
        for (SpellAbility sa : landAbilities) {
            Card land = sa.getHostCard();
            System.out.println("  " + optionNum + ". Play land: " + land.getName());
            optionNum++;
        }

        // Show creature options
        for (SpellAbility sa : creatureAbilities) {
            Card creature = sa.getHostCard();
            System.out.println("  " + optionNum + ". Cast creature: " + creature.getName() +
                " (" + creature.getNetPower() + "/" + creature.getNetToughness() + ") - " +
                creature.getManaCost());
            optionNum++;
        }

        // Show artifact options
        for (SpellAbility sa : artifactAbilities) {
            Card artifact = sa.getHostCard();
            System.out.println("  " + optionNum + ". Cast artifact: " + artifact.getName() +
                " - " + artifact.getManaCost());
            optionNum++;
        }

        // Show sorcery options
        for (SpellAbility sa : sorceryAbilities) {
            Card sorcery = sa.getHostCard();
            System.out.println("  " + optionNum + ". Cast sorcery: " + sorcery.getName() +
                " - " + sorcery.getManaCost());
            optionNum++;
        }

        // Show instant options
        for (SpellAbility sa : instantAbilities) {
            Card instant = sa.getHostCard();
            System.out.println("  " + optionNum + ". Cast instant: " + instant.getName() +
                " - " + instant.getManaCost());
            optionNum++;
        }

        // Show activated ability options
        for (SpellAbility sa : activatedAbilities) {
            Card source = sa.getHostCard();
            String description = sa.getDescription();
            if (description == null || description.isEmpty()) {
                description = "Activate ability";
            }
            System.out.println("  " + optionNum + ". " + source.getName() + ": " + description);
            optionNum++;
        }

        // Get user input
        int choice = getIntInput(0, totalActions);

        // Track choice statistics
        totalChoicesMade++;
        totalChoiceOptions += (totalActions + 1); // +1 for pass option

        if (choice == 0) {
            System.out.println(">> Passing priority...\n");
            return null; // Pass priority
        } else {
            // Find the chosen ability
            SpellAbility chosen = null;
            int idx = choice - 1;

            if (idx < landAbilities.size()) {
                chosen = landAbilities.get(idx);
                System.out.println(">> Playing " + chosen.getHostCard().getName() + "...\n");
            } else if ((idx -= landAbilities.size()) < creatureAbilities.size()) {
                chosen = creatureAbilities.get(idx);
                System.out.println(">> Casting " + chosen.getHostCard().getName() + "...\n");
            } else if ((idx -= creatureAbilities.size()) < artifactAbilities.size()) {
                chosen = artifactAbilities.get(idx);
                System.out.println(">> Casting " + chosen.getHostCard().getName() + "...\n");
            } else if ((idx -= artifactAbilities.size()) < sorceryAbilities.size()) {
                chosen = sorceryAbilities.get(idx);
                System.out.println(">> Casting " + chosen.getHostCard().getName() + "...\n");
            } else if ((idx -= sorceryAbilities.size()) < instantAbilities.size()) {
                chosen = instantAbilities.get(idx);
                System.out.println(">> Casting " + chosen.getHostCard().getName() + "...\n");
            } else if ((idx -= instantAbilities.size()) < activatedAbilities.size()) {
                chosen = activatedAbilities.get(idx);
                System.out.println(">> Activating " + chosen.getHostCard().getName() + "...\n");
            }

            return Collections.singletonList(chosen);
        }
    }

    /**
     * Get playable land abilities from the player's hand.
     */
    private List<SpellAbility> getPlayableLands() {
        List<SpellAbility> lands = new ArrayList<>();

        for (Card c : player.getCardsIn(ZoneType.Hand)) {
            if (c.isLand()) {
                for (SpellAbility sa : c.getAllPossibleAbilities(player, true)) {
                    if (sa.isLandAbility() && sa.canPlay()) {
                        lands.add(sa);
                        break; // Only need one land ability per land
                    }
                }
            }
        }

        return lands;
    }

    /**
     * Get castable creature or artifact spells from the player's hand.
     * @param creatures if true, get creatures; if false, get artifacts
     */
    private List<SpellAbility> getCastableCreaturesAndArtifacts(boolean creatures) {
        List<SpellAbility> spells = new ArrayList<>();

        for (Card c : player.getCardsIn(ZoneType.Hand)) {
            // Check if we're looking for the right type
            if (creatures && !c.isCreature()) continue;
            if (!creatures && !c.isArtifact()) continue;
            // Skip if it's also a land (like artifact lands)
            if (c.isLand()) continue;

            // Get the main spell ability (casting the card)
            for (SpellAbility sa : c.getAllPossibleAbilities(player, true)) {
                // We want spell abilities that can be cast from hand
                if (sa.isSpell() && sa.canPlay()) {
                    // Check if player can actually pay the mana cost right now
                    sa.setActivatingPlayer(player);
                    if (ComputerUtilMana.canPayManaCost(sa, player, 0, false)) {
                        spells.add(sa);
                    }
                    break; // Only need the first castable ability
                }
            }
        }

        return spells;
    }

    /**
     * Get castable sorcery spells from the player's hand.
     */
    private List<SpellAbility> getCastableSorceries() {
        List<SpellAbility> spells = new ArrayList<>();

        for (Card c : player.getCardsIn(ZoneType.Hand)) {
            if (!c.isSorcery()) continue;

            // Get the main spell ability (casting the card)
            for (SpellAbility sa : c.getAllPossibleAbilities(player, true)) {
                // We want spell abilities that can be cast from hand
                if (sa.isSpell() && sa.canPlay()) {
                    // Check if player can actually pay the mana cost right now
                    sa.setActivatingPlayer(player);
                    if (ComputerUtilMana.canPayManaCost(sa, player, 0, false)) {
                        spells.add(sa);
                    }
                    break; // Only need the first castable ability
                }
            }
        }

        return spells;
    }

    /**
     * Get castable instant spells from the player's hand.
     */
    private List<SpellAbility> getCastableInstants() {
        List<SpellAbility> spells = new ArrayList<>();

        for (Card c : player.getCardsIn(ZoneType.Hand)) {
            if (!c.isInstant()) continue;

            // Get the main spell ability (casting the card)
            for (SpellAbility sa : c.getAllPossibleAbilities(player, true)) {
                // We want spell abilities that can be cast from hand
                if (sa.isSpell() && sa.canPlay()) {
                    // Check if player can actually pay the mana cost right now
                    sa.setActivatingPlayer(player);
                    if (ComputerUtilMana.canPayManaCost(sa, player, 0, false)) {
                        spells.add(sa);
                    }
                    break; // Only need the first castable ability
                }
            }
        }

        return spells;
    }

    /**
     * Get activated abilities from permanents on the battlefield.
     */
    private List<SpellAbility> getActivatedAbilities() {
        List<SpellAbility> abilities = new ArrayList<>();

        // Check all permanents we control
        for (Card c : player.getCardsIn(ZoneType.Battlefield)) {
            // Get all abilities for this permanent
            for (SpellAbility sa : c.getAllPossibleAbilities(player, true)) {
                // We want activated abilities (not spells, not triggered)
                // Activated abilities are not spells and can be played from the battlefield
                if (!sa.isSpell() && sa.canPlay() && !sa.isTrigger()) {
                    // Check if player can pay the activation cost
                    sa.setActivatingPlayer(player);
                    // Use simpler check - just see if it's playable
                    // TODO: Could add more sophisticated cost checking here
                    abilities.add(sa);
                }
            }
        }

        return abilities;
    }

    /**
     * Display the current game state to the user.
     */
    private void displayGameState() {
        Game game = getGame();
        PhaseHandler ph = game.getPhaseHandler();

        System.out.println();
        System.out.println("=".repeat(60));
        System.out.println("Turn " + ph.getTurn() + " - " + ph.getPlayerTurn().getName() + "'s turn");
        System.out.println("Phase: " + ph.getPhase().nameForUi);

        Player priorityPlayer = ph.getPriorityPlayer();
        if (priorityPlayer != null) {
            System.out.println("Priority: " + priorityPlayer.getName());
        }

        // Display stack contents if not empty
        if (game.getStack().isEmpty()) {
            System.out.println("Stack: Empty");
        } else {
            System.out.println("Stack: " + game.getStack().size() + " item(s)");
            // Show stack contents from top to bottom
            var stackItems = game.getStack();
            int stackPos = stackItems.size();
            for (var item : stackItems) {
                SpellAbility sa = item.getSpellAbility();
                if (sa != null) {
                    Card source = sa.getHostCard();
                    String controller = item.getActivatingPlayer() != null ?
                        item.getActivatingPlayer().getName() : "Unknown";
                    System.out.println("  " + stackPos + ". " + source.getName() +
                        " (" + controller + ")");
                    if (sa.hasParam("Description")) {
                        System.out.println("      " + sa.getParam("Description"));
                    }
                    stackPos--;
                }
            }
        }
        System.out.println();

        // Display all players
        for (Player p : game.getPlayers()) {
            displayPlayerInfo(p, game);
        }

        System.out.println("-".repeat(60));
    }

    /**
     * Display information about a single player.
     */
    private void displayPlayerInfo(Player p, Game game) {
        boolean isCurrentPlayer = p == game.getPhaseHandler().getPlayerTurn();
        boolean isThisPlayer = p == player;

        String marker;
        if (isThisPlayer) {
            marker = ">>> [YOU] ";
        } else if (isCurrentPlayer) {
            marker = ">>> ";
        } else {
            marker = "    ";
        }

        System.out.println(marker + p.getName());
        System.out.println(marker + "  Life: " + p.getLife());
        System.out.println(marker + "  Hand: " + p.getZone(ZoneType.Hand).size() + " cards");

        if (isThisPlayer) {
            // Show the human player their hand
            List<Card> hand = new ArrayList<>();
            for (Card c : p.getCardsIn(ZoneType.Hand)) {
                hand.add(c);
            }
            if (!hand.isEmpty()) {
                System.out.println(marker + "  Your hand:");
                for (Card c : hand) {
                    System.out.println(marker + "    - " + c.getName());
                }
            }
        }

        System.out.println(marker + "  Library: " + p.getZone(ZoneType.Library).size() + " cards");
        System.out.println(marker + "  Graveyard: " + p.getZone(ZoneType.Graveyard).size() + " cards");
        System.out.println(marker + "  Lands played this turn: " + p.getLandsPlayedThisTurn());

        // Show battlefield
        List<Card> lands = new ArrayList<>();
        List<Card> creatures = new ArrayList<>();
        List<Card> others = new ArrayList<>();

        for (Card c : p.getCardsIn(ZoneType.Battlefield)) {
            if (c.isLand()) {
                lands.add(c);
            } else if (c.isCreature()) {
                creatures.add(c);
            } else {
                others.add(c);
            }
        }

        System.out.println(marker + "  Lands in play: " + lands.size());
        if (!lands.isEmpty() && (isThisPlayer || lands.size() <= 10)) {
            for (Card land : lands) {
                System.out.println(marker + "    - " + land.getName() + (land.isTapped() ? " (tapped)" : ""));
            }
        }

        System.out.println(marker + "  Creatures: " + creatures.size());
        if (!creatures.isEmpty() && (isThisPlayer || creatures.size() <= 10)) {
            for (Card creature : creatures) {
                System.out.println(marker + "    - " + creature.getName() +
                    " (" + creature.getNetPower() + "/" + creature.getNetToughness() + ")" +
                    (creature.isTapped() ? " (tapped)" : "") +
                    (creature.isSick() ? " (summoning sickness)" : ""));
            }
        }

        if (!others.isEmpty()) {
            System.out.println(marker + "  Other permanents: " + others.size());
            if (isThisPlayer || others.size() <= 5) {
                for (Card other : others) {
                    System.out.println(marker + "    - " + other.getName());
                }
            }
        }

        System.out.println();
    }

    /**
     * Get integer input from the user within a specified range.
     * Also handles special commands: "?" for help, "v" for viewing cards, "g" for graveyards.
     */
    private int getIntInput(int min, int max) {
        while (true) {
            System.out.print("Enter choice (" + min + "-" + max + ", or ? for help): ");

            try {
                String line = reader.readLine();
                if (line == null) {
                    System.out.println("Input error, using default choice: " + min);
                    return min;
                }

                line = line.trim();
                if (line.isEmpty()) {
                    System.out.println("No input provided, using default choice: " + min);
                    return min;
                }

                // Handle special commands
                if (line.equals("?")) {
                    showHelp();
                    continue;
                }

                if (line.equalsIgnoreCase("v")) {
                    viewCard();
                    continue;
                }

                if (line.equalsIgnoreCase("g")) {
                    viewGraveyards();
                    continue;
                }

                int choice = Integer.parseInt(line);
                if (choice >= min && choice <= max) {
                    return choice;
                }

                System.out.println("Invalid choice. Please enter a number between " + min + " and " + max + ".");
            } catch (IOException e) {
                System.err.println("Error reading input: " + e.getMessage());
                System.out.println("Using default choice: " + min);
                return min;
            } catch (NumberFormatException e) {
                System.out.println("Invalid input. Please enter a number, '?' for help, 'v' to view a card, or 'g' to view graveyards.");
            }
        }
    }

    /**
     * Display help information for the TUI interface.
     */
    private void showHelp() {
        System.out.println();
        System.out.println("=== HELP ===");
        System.out.println("Commands:");
        System.out.println("  0-9       - Select an action by number");
        System.out.println("  ?         - Show this help");
        System.out.println("  v         - View a card (see detailed card text)");
        System.out.println("  g         - View all graveyards");
        System.out.println();
        System.out.println("During your turn, you can:");
        System.out.println("  - Play lands (if you haven't used your land drop)");
        System.out.println("  - Cast spells from your hand");
        System.out.println("  - Pass priority (0) to move to the next phase");
        System.out.println();
        System.out.println("You will be prompted repeatedly until you pass priority.");
        System.out.println("============");
        System.out.println();
    }

    /**
     * Allow the user to view detailed information about a card.
     * Shows cards from hand and both players' battlefields.
     */
    private void viewCard() {
        Game game = getGame();
        List<Card> allCards = new ArrayList<>();

        // Collect cards from player's hand
        for (Card c : player.getCardsIn(ZoneType.Hand)) {
            allCards.add(c);
        }

        // Collect cards from all battlefields
        for (Player p : game.getPlayers()) {
            for (Card c : p.getCardsIn(ZoneType.Battlefield)) {
                allCards.add(c);
            }
        }

        if (allCards.isEmpty()) {
            System.out.println("No cards to view.");
            return;
        }

        // Remove duplicates by card name (keep first occurrence)
        List<Card> uniqueCards = new ArrayList<>();
        java.util.Set<String> seenNames = new java.util.HashSet<>();
        for (Card c : allCards) {
            if (!seenNames.contains(c.getName())) {
                uniqueCards.add(c);
                seenNames.add(c.getName());
            }
        }

        // Sort alphabetically by name
        uniqueCards.sort((c1, c2) -> c1.getName().compareTo(c2.getName()));

        System.out.println();
        System.out.println("=== VIEW CARD ===");
        System.out.println("Select a card to view:");

        for (int i = 0; i < uniqueCards.size(); i++) {
            Card c = uniqueCards.get(i);
            String location;
            if (c.getZone().is(ZoneType.Hand)) {
                location = "[Hand]";
            } else {
                location = "[Battlefield - " + c.getController().getName() + "]";
            }
            System.out.println("  " + i + ". " + c.getName() + " " + location);
        }

        System.out.print("Enter card number (or press Enter to cancel): ");
        try {
            String line = reader.readLine();
            if (line == null || line.trim().isEmpty()) {
                System.out.println("Cancelled.");
                return;
            }

            int cardIndex = Integer.parseInt(line.trim());
            if (cardIndex < 0 || cardIndex >= uniqueCards.size()) {
                System.out.println("Invalid card number.");
                return;
            }

            Card selectedCard = uniqueCards.get(cardIndex);
            displayCardDetails(selectedCard);

        } catch (IOException e) {
            System.err.println("Error reading input: " + e.getMessage());
        } catch (NumberFormatException e) {
            System.out.println("Invalid input.");
        }
    }

    /**
     * Display detailed information about a card.
     */
    private void displayCardDetails(Card card) {
        System.out.println();
        System.out.println("=".repeat(60));
        System.out.println(card.getName() + " " + card.getManaCost());
        System.out.println("-".repeat(60));

        // Show card type line
        StringBuilder typeLine = new StringBuilder();
        if (!card.getType().isEmpty()) {
            typeLine.append(card.getType().toString());
        }

        System.out.println("Type: " + typeLine.toString());

        // Show creature stats if applicable
        if (card.isCreature()) {
            System.out.println("Power/Toughness: " + card.getNetPower() + "/" + card.getNetToughness());
        }

        // Show oracle text
        String oracleText = card.getOracleText();
        if (oracleText != null && !oracleText.isEmpty()) {
            System.out.println();
            System.out.println("Text:");
            // Replace literal \n with actual newlines
            String formattedText = oracleText.replace("\\n", "\n");
            System.out.println(formattedText);
        }

        // Show current state if on battlefield
        if (card.getZone().is(ZoneType.Battlefield)) {
            System.out.println();
            System.out.println("Current State:");
            System.out.println("  Controller: " + card.getController().getName());
            if (card.isTapped()) {
                System.out.println("  [TAPPED]");
            }
            if (card.isSick()) {
                System.out.println("  [Summoning Sickness]");
            }
        }

        System.out.println("=".repeat(60));
        System.out.println();
    }

    /**
     * Display the contents of all players' graveyards.
     */
    private void viewGraveyards() {
        Game game = getGame();
        System.out.println();
        System.out.println("=== GRAVEYARDS ===");

        for (Player p : game.getPlayers()) {
            List<Card> graveyard = new ArrayList<>();
            for (Card c : p.getCardsIn(ZoneType.Graveyard)) {
                graveyard.add(c);
            }

            System.out.println();
            System.out.println(p.getName() + "'s Graveyard (" + graveyard.size() + " cards):");

            if (graveyard.isEmpty()) {
                System.out.println("  (empty)");
            } else {
                // Sort by card type for better readability
                graveyard.sort((c1, c2) -> {
                    // Sort order: Creature, Instant, Sorcery, Artifact, Enchantment, Land, Other
                    int priority1 = getCardTypePriority(c1);
                    int priority2 = getCardTypePriority(c2);
                    if (priority1 != priority2) {
                        return Integer.compare(priority1, priority2);
                    }
                    return c1.getName().compareTo(c2.getName());
                });

                for (Card c : graveyard) {
                    String cardInfo = "  - " + c.getName();
                    if (c.isCreature()) {
                        cardInfo += " (" + c.getNetPower() + "/" + c.getNetToughness() + ")";
                    }
                    cardInfo += " - " + getCardTypeString(c);
                    System.out.println(cardInfo);
                }
            }
        }

        System.out.println();
        System.out.println("==================");
        System.out.println();
    }

    /**
     * Get a priority value for sorting cards by type.
     */
    private int getCardTypePriority(Card c) {
        if (c.isCreature()) return 1;
        if (c.isInstant()) return 2;
        if (c.isSorcery()) return 3;
        if (c.isArtifact()) return 4;
        if (c.isEnchantment()) return 5;
        if (c.isLand()) return 6;
        return 7;
    }

    /**
     * Get a simple type string for a card.
     */
    private String getCardTypeString(Card c) {
        if (c.isCreature()) return "Creature";
        if (c.isInstant()) return "Instant";
        if (c.isSorcery()) return "Sorcery";
        if (c.isArtifact()) return "Artifact";
        if (c.isEnchantment()) return "Enchantment";
        if (c.isLand()) return "Land";
        return "Other";
    }

    @Override
    public void declareAttackers(Player attacker, Combat combat) {
        // Print any new game log entries
        TUIGuiBase.printNewLogEntries();

        // Get all possible attackers
        CardCollection possibleAttackers = CombatUtil.getPossibleAttackers(attacker);

        if (possibleAttackers.isEmpty()) {
            System.out.println(">> No creatures available to attack. Skipping combat...\n");
            return;
        }

        System.out.println();
        System.out.println("=".repeat(60));
        System.out.println("=== DECLARE ATTACKERS ===");
        System.out.println("=".repeat(60));

        // Show available attackers
        System.out.println("\nAvailable attackers:");
        for (int i = 0; i < possibleAttackers.size(); i++) {
            Card creature = possibleAttackers.get(i);
            System.out.println("  " + i + ". " + creature.getName() +
                " (" + creature.getNetPower() + "/" + creature.getNetToughness() + ")" +
                (creature.isTapped() ? " (tapped)" : "") +
                (creature.isSick() ? " (summoning sickness)" : ""));
        }

        System.out.println("\nChoose attackers one at a time (or enter 'done' when finished):");

        // Get possible defenders
        FCollectionView<GameEntity> possibleDefenders = CombatUtil.getAllPossibleDefenders(attacker);
        List<GameEntity> defendersList = new ArrayList<>();
        for (GameEntity defender : possibleDefenders) {
            defendersList.add(defender);
        }

        // Let user select attackers
        List<Card> selectedAttackers = new ArrayList<>();
        while (true) {
            System.out.print("Enter attacker number (or 'done'): ");
            try {
                String line = reader.readLine();
                if (line == null || line.trim().isEmpty() || line.trim().equalsIgnoreCase("done")) {
                    break;
                }

                int attackerIndex = Integer.parseInt(line.trim());
                if (attackerIndex < 0 || attackerIndex >= possibleAttackers.size()) {
                    System.out.println("Invalid attacker number.");
                    continue;
                }

                Card selectedAttacker = possibleAttackers.get(attackerIndex);

                // Check if already selected
                if (selectedAttackers.contains(selectedAttacker)) {
                    System.out.println(selectedAttacker.getName() + " is already attacking.");
                    continue;
                }

                // If only one defender, attack them automatically
                GameEntity defender = null;
                if (defendersList.size() == 1) {
                    defender = defendersList.get(0);
                    System.out.println(">> " + selectedAttacker.getName() + " attacks " + getDefenderName(defender));
                } else {
                    // Multiple defenders - let user choose
                    System.out.println("\nChoose defender for " + selectedAttacker.getName() + ":");
                    for (int i = 0; i < defendersList.size(); i++) {
                        System.out.println("  " + i + ". " + getDefenderName(defendersList.get(i)));
                    }

                    System.out.print("Enter defender number: ");
                    String defenderLine = reader.readLine();
                    if (defenderLine == null || defenderLine.trim().isEmpty()) {
                        System.out.println("Cancelled attacker selection.");
                        continue;
                    }

                    int defenderIndex = Integer.parseInt(defenderLine.trim());
                    if (defenderIndex < 0 || defenderIndex >= defendersList.size()) {
                        System.out.println("Invalid defender number.");
                        continue;
                    }
                    defender = defendersList.get(defenderIndex);
                    System.out.println(">> " + selectedAttacker.getName() + " attacks " + getDefenderName(defender));
                }

                // Add the attacker
                combat.addAttacker(selectedAttacker, defender);
                selectedAttackers.add(selectedAttacker);

                System.out.println("  Current attackers: " + selectedAttackers.size());

            } catch (IOException e) {
                System.err.println("Error reading input: " + e.getMessage());
                break;
            } catch (NumberFormatException e) {
                System.out.println("Invalid input. Enter a number or 'done'.");
            }
        }

        if (selectedAttackers.isEmpty()) {
            System.out.println(">> No attackers declared.\n");
        } else {
            System.out.println(">> Declared " + selectedAttackers.size() + " attacker(s).\n");
        }
    }

    @Override
    public void declareBlockers(Player defender, Combat combat) {
        // Print any new game log entries
        TUIGuiBase.printNewLogEntries();

        // Get all attackers
        CardCollection attackers = combat.getAttackers();

        if (attackers.isEmpty()) {
            System.out.println(">> No attackers to block.\n");
            return;
        }

        // Get possible blockers
        CardCollection possibleBlockers = defender.getCreaturesInPlay();
        if (possibleBlockers.isEmpty()) {
            System.out.println(">> No creatures available to block.\n");
            return;
        }

        System.out.println();
        System.out.println("=".repeat(60));
        System.out.println("=== DECLARE BLOCKERS ===");
        System.out.println("=".repeat(60));

        // Show attackers
        System.out.println("\nAttacking creatures:");
        for (int i = 0; i < attackers.size(); i++) {
            Card attacker = attackers.get(i);
            GameEntity defendingEntity = combat.getDefenderByAttacker(attacker);
            System.out.println("  " + i + ". " + attacker.getName() +
                " (" + attacker.getNetPower() + "/" + attacker.getNetToughness() + ")" +
                " attacking " + getDefenderName(defendingEntity));
        }

        // Show available blockers
        System.out.println("\nAvailable blockers:");
        for (int i = 0; i < possibleBlockers.size(); i++) {
            Card creature = possibleBlockers.get(i);
            System.out.println("  " + i + ". " + creature.getName() +
                " (" + creature.getNetPower() + "/" + creature.getNetToughness() + ")" +
                (creature.isTapped() ? " (tapped)" : ""));
        }

        System.out.println("\nDeclare blockers (or enter 'done' when finished):");
        System.out.println("Format: <blocker_num> blocks <attacker_num>");
        System.out.println("Example: 0 blocks 1");

        // Let user assign blockers
        while (true) {
            System.out.print("Enter block assignment (or 'done'): ");
            try {
                String line = reader.readLine();
                if (line == null || line.trim().isEmpty() || line.trim().equalsIgnoreCase("done")) {
                    break;
                }

                // Parse "X blocks Y" format
                String[] parts = line.trim().split("\\s+");
                if (parts.length != 3 || !parts[1].equalsIgnoreCase("blocks")) {
                    System.out.println("Invalid format. Use: <blocker_num> blocks <attacker_num>");
                    continue;
                }

                int blockerIndex = Integer.parseInt(parts[0]);
                int attackerIndex = Integer.parseInt(parts[2]);

                if (blockerIndex < 0 || blockerIndex >= possibleBlockers.size()) {
                    System.out.println("Invalid blocker number.");
                    continue;
                }

                if (attackerIndex < 0 || attackerIndex >= attackers.size()) {
                    System.out.println("Invalid attacker number.");
                    continue;
                }

                Card blocker = possibleBlockers.get(blockerIndex);
                Card attacker = attackers.get(attackerIndex);

                // Check if blocker can block this attacker
                if (!CombatUtil.canBlock(attacker, blocker, combat)) {
                    System.out.println(blocker.getName() + " cannot block " + attacker.getName() + ".");
                    continue;
                }

                // Add the blocker
                combat.addBlocker(attacker, blocker);
                System.out.println(">> " + blocker.getName() + " blocks " + attacker.getName());

            } catch (IOException e) {
                System.err.println("Error reading input: " + e.getMessage());
                break;
            } catch (NumberFormatException e) {
                System.out.println("Invalid input. Use: <blocker_num> blocks <attacker_num>");
            } catch (ArrayIndexOutOfBoundsException e) {
                System.out.println("Invalid format. Use: <blocker_num> blocks <attacker_num>");
            }
        }

        System.out.println(">> Blockers declared.\n");
    }

    /**
     * Get a human-readable name for a defender (player or planeswalker).
     */
    private String getDefenderName(GameEntity defender) {
        if (defender instanceof Player) {
            Player p = (Player) defender;
            return p.getName() + " (Life: " + p.getLife() + ")";
        }
        // For planeswalkers and other entities
        return defender.toString();
    }

    @Override
    public boolean confirmTrigger(forge.game.trigger.WrappedAbility wrapper) {
        // Mandatory triggers always fire
        if (wrapper.isMandatory()) {
            return true;
        }

        // Optional triggers - ask the user
        SpellAbility sa = wrapper.getWrappedAbility();
        Card source = sa.getHostCard();

        System.out.println();
        System.out.println("=".repeat(60));
        System.out.println("=== OPTIONAL TRIGGER ===");
        System.out.println("=".repeat(60));
        System.out.println("Source: " + source.getName());
        System.out.println("Ability: " + sa.getDescription());
        System.out.println();
        System.out.print("Do you want to use this trigger? (y/n): ");

        try {
            String line = reader.readLine();
            if (line == null || line.trim().isEmpty()) {
                return false; // Default to no
            }

            String response = line.trim().toLowerCase();
            boolean result = response.startsWith("y");

            if (result) {
                System.out.println(">> Trigger accepted\n");
            } else {
                System.out.println(">> Trigger declined\n");
            }

            return result;
        } catch (IOException e) {
            System.err.println("Error reading input: " + e.getMessage());
            return false; // Default to no on error
        }
    }
}
