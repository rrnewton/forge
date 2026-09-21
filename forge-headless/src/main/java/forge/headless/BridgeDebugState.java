package forge.headless;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import forge.card.MagicColor;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.player.Player;
import forge.game.phase.PhaseType;
import forge.game.zone.ZoneType;

/** Local test-only rendezvous. All snapshots are captured on the game thread. */
final class BridgeDebugState {
    private final Game game;
    private final List<Player> players;
    private final CompletableFuture<JsonNode> initialization = new CompletableFuture<>();
    private final Deque<ObjectNode> snapshots = new ArrayDeque<>();
    private final Deque<Command> commands = new ArrayDeque<>();
    private final Map<String, String> identities = new HashMap<>();
    private final Deque<JsonNode> damagePlans = new ArrayDeque<>();
    private final CompletableFuture<Void> failure = new CompletableFuture<>();
    private Throwable fatalFailure;
    private final List<JsonNode> pendingDamage = new ArrayList<>();
    private boolean initialized;

    void submitDamagePlan(JsonNode plan) {
        put(damagePlans, plan.deepCopy());
    }

    JsonNode takeDamageAssignment(JsonNode attacker) {
        if (pendingDamage.isEmpty()) {
            JsonNode plan = take(damagePlans);
            boolean firstStrike = game.getPhaseHandler().getPhase() == PhaseType.COMBAT_FIRST_STRIKE_DAMAGE;
            if (!plan.path("first_strike_step").isBoolean()
                    || plan.path("first_strike_step").asBoolean() != firstStrike) {
                throw new IllegalStateException("Damage plan arrived in the wrong combat step: " + plan);
            }
            plan.path("assignments").forEach(pendingDamage::add);
        }
        for (int index = 0; index < pendingDamage.size(); index++) {
            JsonNode entry = pendingDamage.get(index);
            JsonNode candidate = entry.path("attacker");
            if (candidate.path("name").equals(attacker.path("name"))
                    && candidate.path("idx").equals(attacker.path("idx"))
                    && candidate.path("controller").equals(attacker.path("controller"))) {
                pendingDamage.remove(index);
                return entry.path("blockers");
            }
        }
        throw new IllegalStateException("No exact remote damage assignment for " + attacker);
    }

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
        if (!pendingDamage.isEmpty() || !damagePlans.isEmpty()) {
            throw new IllegalStateException("Unconsumed combat damage input at state checkpoint");
        }
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

    /** Wake every protocol/game-thread waiter with the original failure. */
    synchronized void fail(Throwable cause) {
        if (fatalFailure == null) {
            fatalFailure = cause;
            failure.completeExceptionally(cause);
            notifyAll();
        }
    }

    private void throwIfFailed() {
        if (fatalFailure != null) {
            throw new IllegalStateException("Forge debug game failed: " + fatalFailure, fatalFailure);
        }
    }

    private synchronized <T> T take(Deque<T> queue) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (true) {
            throwIfFailed();
            if (!queue.isEmpty()) {
                T value = queue.removeFirst();
                notifyAll();
                return value;
            }
            waitForQueue(deadline);
        }
    }

    private synchronized <T> void put(Deque<T> queue, T value) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (true) {
            throwIfFailed();
            if (queue.isEmpty()) {
                queue.addLast(value);
                notifyAll();
                return;
            }
            waitForQueue(deadline);
        }
    }

    private void waitForQueue(long deadline) {
        long remaining = deadline - System.nanoTime();
        if (remaining <= 0) {
            throw new IllegalStateException("Debug bridge boundary timed out");
        }
        try {
            TimeUnit.NANOSECONDS.timedWait(this, remaining);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Debug bridge interrupted", error);
        }
    }

    private <T> T await(CompletableFuture<T> future) {
        try {
            CompletableFuture.anyOf(future, failure).get(30, TimeUnit.SECONDS);
            return future.join();
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
