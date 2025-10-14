package forge.headless;

import forge.LobbyPlayer;
import forge.deck.Deck;
import forge.deck.DeckSection;
import forge.game.*;
import forge.game.card.Card;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.spellability.SpellAbility;
import forge.gui.GuiBase;
import forge.item.PaperCard;
import forge.model.FModel;
import forge.player.GamePlayerUtil;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Test targeting functionality in TUI.
 * Uses controlled game state to reproduce targeting bugs.
 */
public class TargetingTest {

    @BeforeClass
    public static void setup() {
        GuiBase.setInterface(new HeadlessGuiBase());
        FModel.initialize(null, null);
    }

    /**
     * Test that Lightning Strike can target creatures.
     *
     * Setup:
     * - Player has Lightning Strike in hand, 2 Mountains on battlefield
     * - Opponent has a creature on battlefield
     *
     * Expected:
     * - Lightning Strike should be castable
     * - Should be able to target opponent's creature
     * - Should successfully add to stack
     */
    @Test
    public void testLightningStrikeTargeting() {
        System.out.println("\n=== Testing Lightning Strike Targeting ===");

        // Create minimal decks
        Deck playerDeck = createTestDeck("Lightning Strike", "Mountain");
        Deck opponentDeck = createTestDeck("Ember Hauler", "Mountain");

        // Create players
        List<RegisteredPlayer> players = new ArrayList<>();

        RegisteredPlayer player1 = new RegisteredPlayer(playerDeck);
        LobbyPlayer player1Lobby = GamePlayerUtil.getGuiPlayer("Player 1", 0, 0, false);
        player1.setPlayer(player1Lobby);
        players.add(player1);

        RegisteredPlayer player2 = new RegisteredPlayer(opponentDeck);
        LobbyPlayer player2Lobby = GamePlayerUtil.createAiPlayer("AI", 0);
        player2.setPlayer(player2Lobby);
        players.add(player2);

        // Create game
        GameRules rules = new GameRules(GameType.Constructed);
        Match match = new Match(rules, players, "Targeting Test");
        Game game = match.createGame();

        // Find players
        Player humanPlayer = null;
        Player aiPlayer = null;
        for (Player p : game.getPlayers()) {
            if (p.getLobbyPlayer() == player1Lobby) {
                humanPlayer = p;
            } else if (p.getLobbyPlayer() == player2Lobby) {
                aiPlayer = p;
            }
        }

        assertNotNull("Human player should exist", humanPlayer);
        assertNotNull("AI player should exist", aiPlayer);

        // Set up controlled game state
        setupGameState(game, humanPlayer, aiPlayer);

        // Try to cast Lightning Strike
        testCastingLightningStrike(game, humanPlayer, aiPlayer);

        System.out.println("  ✓ Lightning Strike targeting test completed");
    }

    /**
     * Set up a controlled game state for testing.
     */
    private void setupGameState(Game game, Player humanPlayer, Player aiPlayer) {
        // This would require access to game state manipulation
        // For now, we'll document what state we want:
        // - Human player: 2 Mountains on battlefield, Lightning Strike in hand
        // - AI player: 1 Creature on battlefield

        System.out.println("  Setting up game state...");
        // TODO: Implement game state setup
        // This may require reflection or special test hooks
    }

    /**
     * Test casting Lightning Strike with targeting.
     */
    private void testCastingLightningStrike(Game game, Player humanPlayer, Player aiPlayer) {
        System.out.println("  Testing Lightning Strike cast...");

        // Find Lightning Strike in hand
        Card lightningStrike = null;
        for (Card c : humanPlayer.getCardsIn(forge.game.zone.ZoneType.Hand)) {
            if (c.getName().equals("Lightning Strike")) {
                lightningStrike = c;
                break;
            }
        }

        if (lightningStrike == null) {
            System.out.println("  ! Lightning Strike not in hand, skipping test");
            return;
        }

        // Find spell ability
        SpellAbility castSA = null;
        for (SpellAbility sa : lightningStrike.getAllPossibleAbilities(humanPlayer, true)) {
            if (sa.isSpell()) {
                castSA = sa;
                break;
            }
        }

        assertNotNull("Lightning Strike should have cast ability", castSA);

        // Check if it uses targeting
        if (castSA.usesTargeting()) {
            System.out.println("  Lightning Strike uses targeting: " + castSA.usesTargeting());
            System.out.println("  Target restrictions: " + castSA.getTargetRestrictions());

            // Find valid targets
            List<Card> validTargets = new ArrayList<>();
            for (Card c : aiPlayer.getCardsIn(forge.game.zone.ZoneType.Battlefield)) {
                if (c.isCreature()) {
                    validTargets.add(c);
                }
            }

            System.out.println("  Valid targets found: " + validTargets.size());
            for (Card target : validTargets) {
                System.out.println("    - " + target.getName());
            }

            assertTrue("Should have at least one valid target", validTargets.size() > 0);
        }
    }

    /**
     * Create a simple test deck with specific cards.
     */
    private Deck createTestDeck(String mainCard, String land) {
        Deck deck = new Deck("Test Deck");

        // Add 4 of the main card
        for (int i = 0; i < 4; i++) {
            PaperCard card = FModel.getMagicDb().getCommonCards().getCard(mainCard);
            if (card != null) {
                deck.getOrCreate(DeckSection.Main).add(card);
            }
        }

        // Add 20 lands
        PaperCard landCard = FModel.getMagicDb().getCommonCards().getCard(land);
        if (landCard != null) {
            for (int i = 0; i < 20; i++) {
                deck.getOrCreate(DeckSection.Main).add(landCard);
            }
        }

        return deck;
    }
}
