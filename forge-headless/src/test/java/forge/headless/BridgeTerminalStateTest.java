package forge.headless;

import java.util.ArrayList;
import java.util.List;

import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.card.Card;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.zone.ZoneType;
import forge.gui.GuiBase;
import forge.model.FModel;
import forge.player.GamePlayerUtil;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;

/** Terminal snapshots must include every action in the same CR 704.3 event. */
public class BridgeTerminalStateTest {
    @BeforeClass
    public static void initialize() {
        GuiBase.setInterface(new HeadlessGuiBase());
        FModel.initialize(null, null);
    }

    private Game game(boolean oracle) {
        List<RegisteredPlayer> registered = new ArrayList<>();
        for (int index = 0; index < 2; index++) {
            Deck deck = new Deck("terminal witness");
            deck.getMain().add(FModel.getMagicDb().getCommonCards().getCard("Forest"), 60);
            registered.add(new RegisteredPlayer(deck).setPlayer(GamePlayerUtil.createAiPlayer("P" + index, index)));
        }
        Game game = new Match(new GameRules(GameType.Constructed), registered, "terminal witness").createGame();
        game.setAge(GameStage.Play);
        game.getAction().setDebugCompleteTerminalSba(oracle);
        return game;
    }

    private Card creature(Game game, Player owner, String name, int damage) {
        Card card = Card.fromPaperCard(FModel.getMagicDb().getCommonCards().getCard(name), owner);
        card = game.getAction().moveToPlay(card, owner, null, null);
        card.setDamage(damage);
        return card;
    }

    @Test
    public void legacyOrderingPositiveControlLeavesLethalCreature() {
        Game game = game(false);
        Player loser = game.getPlayers().get(0);
        Card doomed = creature(game, loser, "Grizzly Bears", 2);
        loser.setLife(0, null);
        game.getAction().checkStateEffects(false);
        assertTrue(game.isGameOver());
        assertTrue(loser.hasLost());
        assertTrue("proves the old early-return path executed", doomed.isInZone(ZoneType.Battlefield));
    }

    @Test
    public void oracleCompletesLethalDestructionOnBothSidesBeforePublishing() {
        Game game = game(true);
        Player loser = game.getPlayers().get(0);
        Player winner = game.getPlayers().get(1);
        Card losingDoomed = creature(game, loser, "Grizzly Bears", 2);
        Card winningDoomed = creature(game, winner, "Grizzly Bears", 2);
        Card survivor = creature(game, winner, "Craw Wurm", 2);
        loser.setLife(0, null);
        game.getAction().checkStateEffects(false);
        assertTrue(game.isGameOver());
        assertTrue(loser.hasLost());
        assertTrue(winner.hasWon());
        assertTrue(losingDoomed.isInZone(ZoneType.Graveyard));
        assertTrue(winningDoomed.isInZone(ZoneType.Graveyard));
        assertTrue(survivor.isInZone(ZoneType.Battlefield));
        assertEquals(2, survivor.getDamage());
    }

    @Test
    public void outcomesAreDeterminedBeforeSimultaneousPlatinumAngelDestruction() {
        Game game = game(true);
        Player protectedPlayer = game.getPlayers().get(0);
        Player opponent = game.getPlayers().get(1);
        Card angel = creature(game, protectedPlayer, "Platinum Angel", 4);
        game.getAction().checkStaticAbilities();
        protectedPlayer.setLife(0, null);
        opponent.setLife(0, null);
        game.getAction().checkStateEffects(false);
        assertTrue(game.isGameOver());
        assertTrue(opponent.hasLost());
        assertTrue("moving the initial loss check after destruction would incorrectly draw", protectedPlayer.hasWon());
        assertTrue(angel.isInZone(ZoneType.Graveyard));
    }
}
