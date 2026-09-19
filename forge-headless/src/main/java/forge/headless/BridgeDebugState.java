package forge.headless;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import forge.card.MagicColor;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

/** Local test-only rendezvous. All snapshots are captured on the game thread. */
final class BridgeDebugState {
    private final Game game;
    private final List<Player> players;
    private final CompletableFuture<JsonNode> initialization = new CompletableFuture<>();
    private final BlockingQueue<ObjectNode> snapshots = new ArrayBlockingQueue<>(1);
    private final BlockingQueue<Command> commands = new ArrayBlockingQueue<>(1);
    private final Map<String, String> identities = new HashMap<>();
    private boolean initialized;

    BridgeDebugState(Game game, List<Player> players) {
        this.game = game;
        this.players = players;
    }

    void initialize(JsonNode data) {
        if (!initialization.complete(data.deepCopy())) {
            throw new IllegalStateException("Debug initialization is one-shot");
        }
    }

    void initializeOnGameThread() {
        if (initialized) {
            return;
        }
        JsonNode initial = await(initialization);
        initial.path("names").fields().forEachRemaining(entry -> identities.put(entry.getValue().asText(), entry.getKey()));
        for (int index = 0; index < players.size(); index++) {
            Player player = players.get(index);
            JsonNode state = initial.path("state").path("players").get(index);
            List<Card> remaining = new ArrayList<>();
            player.getCardsIn(ZoneType.Hand).forEach(remaining::add);
            player.getCardsIn(ZoneType.Library).forEach(remaining::add);
            List<Card> hand = takeCards(remaining, state.path("hand"));
            List<Card> library = takeCards(remaining, state.path("library"));
            if (!remaining.isEmpty()) {
                throw new IllegalStateException("Debug initial deck differs from Forge deck: " + remaining);
            }
            player.getZone(ZoneType.Hand).setCards(hand);
            player.getZone(ZoneType.Library).setCards(library);
        }
        initialized = true;
    }

    private List<Card> takeCards(List<Card> remaining, JsonNode wanted) {
        List<Card> result = new ArrayList<>();
        for (JsonNode id : wanted) {
            Card found = null;
            for (Card candidate : remaining) {
                if (identity(candidate).equals(id.asText())) {
                    found = candidate;
                    break;
                }
            }
            if (found == null) {
                throw new IllegalStateException("Debug initial card missing: " + id);
            }
            remaining.remove(found);
            result.add(found);
        }
        return result;
    }

    ObjectNode checkpoint() {
        return take(snapshots);
    }

    Command priority() {
        put(snapshots, snapshot(false));
        return take(commands);
    }

    ObjectNode decide() {
        Command command = new Command(null);
        put(commands, command);
        return await(command.result);
    }

    void replay(JsonNode action) {
        put(commands, new Command(action.deepCopy()));
    }

    void finished() {
        if (initialized && game.isGameOver()) {
            put(snapshots, snapshot(true));
        }
    }

    private String identity(Card card) {
        String value = identities.get(card.getName());
        if (value == null) {
            throw new IllegalStateException("Debug snapshot has unmapped card: " + card.getName());
        }
        return value;
    }

    private ArrayNode zone(Player player, ZoneType zone, boolean ordered) {
        List<String> values = new ArrayList<>();
        for (Card card : player.getCardsIn(zone)) {
            values.add(identity(card));
        }
        if (!ordered) {
            Collections.sort(values);
        }
        ArrayNode array = BridgeTransport.JSON.createArrayNode();
        values.forEach(array::add);
        return array;
    }

    private ObjectNode snapshot(boolean terminal) {
        ObjectNode result = BridgeTransport.JSON.createObjectNode();
        result.put("turn", game.getPhaseHandler().getTurn());
        result.put("active_seat", game.getPhaseHandler().getPlayerTurn().getId() + 1);
        result.put("phase", game.getPhaseHandler().getPhase().name().toLowerCase());
        result.put("terminal", terminal);
        result.put("stack_size", game.getStack().size());
        ArrayNode states = result.putArray("players");
        ArrayNode battlefield = result.putArray("battlefield");
        for (Player player : players) {
            ObjectNode state = states.addObject();
            state.put("seat", player.getId() + 1);
            state.put("life", player.getLife());
            state.put("has_lost", player.hasLost());
            state.put("poison", player.getPoisonCounters());
            ObjectNode mana = state.putObject("mana");
            byte[] colors = {MagicColor.WHITE, MagicColor.BLUE, MagicColor.BLACK, MagicColor.RED, MagicColor.GREEN, MagicColor.COLORLESS};
            String[] symbols = {"W", "U", "B", "R", "G", "C"};
            for (int index = 0; index < colors.length; index++) {
                int count = player.getManaPool().getAmountOfColor(colors[index]);
                if (count != 0) {
                    mana.put(symbols[index], count);
                }
            }
            state.set("hand", zone(player, ZoneType.Hand, false));
            state.set("library", zone(player, ZoneType.Library, true));
            state.set("graveyard", zone(player, ZoneType.Graveyard, false));
            state.set("exile", zone(player, ZoneType.Exile, false));
            for (Card card : player.getCardsIn(ZoneType.Battlefield)) {
                ObjectNode permanent = battlefield.addObject();
                permanent.put("identity", identity(card));
                permanent.put("owner", card.getOwner().getId() + 1);
                permanent.put("controller", card.getController().getId() + 1);
                permanent.put("tapped", card.isTapped());
                permanent.put("damage", card.getDamage());
                if (card.isCreature()) {
                    permanent.put("power", card.getNetPower());
                    permanent.put("toughness", card.getNetToughness());
                } else {
                    permanent.putNull("power");
                    permanent.putNull("toughness");
                }
                ObjectNode counters = permanent.putObject("counters");
                card.getCounters().entrySet().forEach(entry -> counters.put(entry.getElement().toString().toLowerCase(java.util.Locale.ROOT), entry.getCount()));
            }
        }
        return result;
    }

    private static <T> T take(BlockingQueue<T> queue) {
        try {
            T value = queue.poll(30, TimeUnit.SECONDS);
            if (value == null) {
                throw new IllegalStateException("Debug bridge boundary timed out");
            }
            return value;
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Debug bridge interrupted", error);
        }
    }

    private static <T> void put(BlockingQueue<T> queue, T value) {
        try {
            if (!queue.offer(value, 30, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Debug bridge queue timed out");
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Debug bridge interrupted", error);
        }
    }

    private static <T> T await(CompletableFuture<T> future) {
        try {
            return future.get(30, TimeUnit.SECONDS);
        } catch (Exception error) {
            throw new IllegalStateException("Debug bridge operation failed", error);
        }
    }

    static final class Command {
        final JsonNode action;
        final CompletableFuture<ObjectNode> result = new CompletableFuture<>();

        Command(JsonNode action) {
            this.action = action;
        }
    }
}
