package forge.headless;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.node.ObjectNode;
import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.spellability.SpellAbility;
import forge.gui.GuiBase;
import forge.model.FModel;
import forge.player.GamePlayerUtil;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;

public class BridgeDebugPriorityTest {
    @BeforeClass
    public static void initialize() {
        GuiBase.setInterface(new HeadlessGuiBase());
        FModel.initialize(null, null);
    }

    private Game game() {
        return game(false);
    }

    private Game game(boolean bridge) {
        List<RegisteredPlayer> registered = new ArrayList<>();
        for (int index = 0; index < 2; index++) {
            Deck deck = new Deck("priority witness");
            deck.getMain().add(FModel.getMagicDb().getCommonCards().getCard("Mountain"), 60);
            registered.add(new RegisteredPlayer(deck).setPlayer(bridge
                    ? new LobbyPlayerBridge("P" + index, index + 1, false, true, 1)
                    : GamePlayerUtil.createAiPlayer("P" + index, index)));
        }
        Game game = new Match(new GameRules(GameType.Constructed), registered, "priority witness").createGame();
        game.setAge(GameStage.Play);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, game.getPlayers().get(0));
        return game;
    }

    private BridgeDebugState state(Game game) {
        BridgeDebugState state = new BridgeDebugState(game, new ArrayList<>(game.getPlayers()), true);
        ObjectNode initial = BridgeTransport.JSON.createObjectNode();
        initial.putObject("names").put("CARD#Bolt", "Lightning Bolt").put("CARD#Shock", "Shock");
        for (Player ignored : game.getPlayers()) {
            ObjectNode player = initial.with("state").withArray("players").addObject();
            player.putArray("hand");
            player.putArray("library");
        }
        state.initialize(initial);
        state.initializeOnGameThread();
        return state;
    }

    private void spell(Game game, Player player, String name) {
        Card card = Card.fromPaperCard(FModel.getMagicDb().getCommonCards().getCard(name), player);
        SpellAbility ability = card.getFirstSpellAbility();
        ability.setActivatingPlayer(player);
        ability.getTargets().add(game.getNextPlayerAfter(player));
        game.getStack().add(ability);
    }

    private CompletableFuture<BridgeDebugState.Command> priority(BridgeDebugState state) {
        return CompletableFuture.supplyAsync(state::priority);
    }

    private ObjectNode context(ObjectNode snapshot) {
        ObjectNode context = BridgeTransport.JSON.createObjectNode();
        for (String field : new String[]{"transaction_id", "priority_seat", "turn", "phase", "active_seat"}) {
            context.set(field, snapshot.get(field));
        }
        return context;
    }

    @Test
    public void nonactivePriorityObservesOrderedResponsesAndRejectsWrongTransaction() throws Exception {
        Game game = game();
        BridgeDebugState state = state(game);
        Player active = game.getPlayers().get(0);
        Player responder = game.getPlayers().get(1);
        spell(game, active, "Lightning Bolt");
        spell(game, responder, "Shock");
        game.getPhaseHandler().setPriority(responder);
        CompletableFuture<BridgeDebugState.Command> waiting = priority(state);
        try {
            ObjectNode snapshot = state.checkpoint();
            assertEquals(1, snapshot.path("active_seat").asInt());
            assertEquals(2, snapshot.path("priority_seat").asInt());
            assertEquals(2, snapshot.path("stack_size").asInt());
            assertEquals("CARD#Shock", snapshot.path("stack").get(0).path("identity").asText());
            assertEquals("CARD#Bolt", snapshot.path("stack").get(1).path("identity").asText());
            assertEquals("spell", snapshot.path("stack").get(0).path("kind").asText());
            ObjectNode correct = context(snapshot);
            for (String field : new String[]{"transaction_id", "priority_seat", "turn", "phase", "active_seat"}) {
                ObjectNode wrong = correct.deepCopy();
                wrong.remove(field);
                try {
                    state.replay(BridgeTransport.JSON.createObjectNode().put("type", "pass"), wrong, 2);
                    fail("missing transaction field accepted: " + field);
                } catch (IllegalStateException expected) {
                    assertTrue(expected.getMessage().contains(field));
                }
            }
            try {
                state.replay(BridgeTransport.JSON.createObjectNode().put("type", "pass"), correct, 1);
                fail("wrong command seat accepted");
            } catch (IllegalStateException expected) {
                assertTrue(expected.getMessage().contains("wrong priority seat"));
            }
            assertFalse("invalid transaction must not release callback", waiting.isDone());
            // A decoded JSON integer may be IntNode even when producer used LongNode.
            correct.put("transaction_id", 1);
            state.replay(BridgeTransport.JSON.createObjectNode().put("type", "pass"), correct, 2);
            assertEquals("pass", waiting.get(2, TimeUnit.SECONDS).action.path("type").asText());
            try {
                state.replay(BridgeTransport.JSON.createObjectNode().put("type", "pass"), correct, 2);
                fail("duplicate transaction accepted");
            } catch (IllegalStateException expected) {
                assertTrue(expected.getMessage().contains("already consumed"));
            }
        } finally {
            state.fail(new IllegalStateException("test finished"));
        }
    }

    @Test
    public void realControllerStopsOutsideMainOnNonactiveStackPriority() throws Exception {
        for (boolean ai : new boolean[]{false, true}) {
            Game game = game();
            BridgeDebugState state = state(game);
            Player active = game.getPlayers().get(0);
            Player responder = game.getPlayers().get(1);
            spell(game, active, "Lightning Bolt");
            game.getPhaseHandler().devModeSet(PhaseType.UPKEEP, active);
            game.getPhaseHandler().setPriority(responder);
            LobbyPlayerBridge lobby = new LobbyPlayerBridge("response witness", 2, ai, true, 1);
            BridgeController controller = new BridgeController(game, responder, lobby, 2, ai, true, 1);
            controller.setDebugState(state);
            CompletableFuture<List<SpellAbility>> choice = CompletableFuture.supplyAsync(controller::chooseSpellAbilityToPlay);
            CompletableFuture<ObjectNode> observed = CompletableFuture.supplyAsync(state::checkpoint);
            try {
                ObjectNode snapshot = observed.get(2, TimeUnit.SECONDS);
                assertEquals("upkeep", snapshot.path("phase").asText());
                assertEquals(2, snapshot.path("priority_seat").asInt());
                assertEquals(1, snapshot.path("stack_size").asInt());
                assertFalse("real controller must wait for the observed transaction", choice.isDone());
                if (ai) {
                    assertEquals("pass", state.decide(context(snapshot), 2).path("type").asText());
                } else {
                    state.replay(BridgeTransport.JSON.createObjectNode().put("type", "pass"), context(snapshot), 2);
                }
                assertNull(choice.get(2, TimeUnit.SECONDS));
            } finally {
                state.fail(new IllegalStateException("test finished"));
            }
        }
    }

    @Test
    public void twoPassesResolveOnlyTopSpellThenActivePlayerGetsFreshPriority() throws Exception {
        Game game = game(true);
        BridgeDebugState state = state(game);
        Player active = game.getPlayers().get(0);
        Player responder = game.getPlayers().get(1);
        for (Player player : game.getPlayers()) {
            ((BridgeController) player.getController()).setDebugState(state);
        }
        spell(game, active, "Lightning Bolt");
        spell(game, responder, "Shock");
        game.getPhaseHandler().setPriority(active);
        game.getPhaseHandler().onStackResolved();
        CompletableFuture<Void> steps = CompletableFuture.runAsync(() -> {
            for (int index = 0; index < 3; index++) {
                game.getPhaseHandler().mainLoopStep();
            }
        });
        try {
            int[] expectedSeats = {1, 2, 1};
            int[] expectedDepths = {2, 2, 1};
            for (int index = 0; index < 3; index++) {
                ObjectNode snapshot = CompletableFuture.supplyAsync(state::checkpoint).get(2, TimeUnit.SECONDS);
                assertEquals(index + 1, snapshot.path("transaction_id").asLong());
                assertEquals(expectedSeats[index], snapshot.path("priority_seat").asInt());
                assertEquals(expectedDepths[index], snapshot.path("stack_size").asInt());
                if (index == 2) {
                    assertEquals("CARD#Bolt", snapshot.path("stack").get(0).path("identity").asText());
                    assertEquals(18, snapshot.path("players").get(0).path("life").asInt());
                    assertEquals(20, snapshot.path("players").get(1).path("life").asInt());
                }
                state.replay(BridgeTransport.JSON.createObjectNode().put("type", "pass"), context(snapshot), expectedSeats[index]);
            }
            steps.get(2, TimeUnit.SECONDS);
            assertEquals(1, game.getStack().size());
        } finally {
            state.fail(new IllegalStateException("test finished"));
        }
    }

    @Test
    public void continuationMustFollowItsOwnPriorityActionAndNextStopIsFresh() throws Exception {
        Game game = game();
        BridgeDebugState state = state(game);
        game.getPhaseHandler().setPriority(game.getPlayers().get(0));
        CompletableFuture<BridgeDebugState.Command> waiting = priority(state);
        try {
            ObjectNode first = context(state.checkpoint());
            try {
                state.validateContinuation(first, 1);
                fail("continuation accepted before action");
            } catch (IllegalStateException expected) {
                assertTrue(expected.getMessage().contains("precedes"));
            }
            state.replay(BridgeTransport.JSON.createObjectNode().put("type", "announce_cast"), first, 1);
            waiting.get(2, TimeUnit.SECONDS);
            state.validateContinuation(first, 1);
            CompletableFuture<BridgeDebugState.Command> secondWaiting = priority(state);
            ObjectNode second = context(state.checkpoint());
            assertEquals(2, second.path("transaction_id").asLong());
            try {
                state.validateContinuation(first, 1);
                fail("stale continuation accepted at next priority");
            } catch (IllegalStateException expected) {
                assertTrue(expected.getMessage().contains("transaction_id"));
            }
            state.replay(BridgeTransport.JSON.createObjectNode().put("type", "pass"), second, 1);
            secondWaiting.get(2, TimeUnit.SECONDS);
        } finally {
            state.fail(new IllegalStateException("test finished"));
        }
    }
}
