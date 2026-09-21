package forge.headless;

import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

import static org.junit.Assert.*;

/** A failed game must wake rendezvous waiters instead of timing out later. */
public class BridgeDebugFailureTest {
    private static Throwable rootCause(Throwable error) {
        while (error.getCause() != null) {
            error = error.getCause();
        }
        return error;
    }

    private void assertFailureWakes(java.util.function.Consumer<BridgeDebugState> operation) throws Exception {
        BridgeDebugState state = new BridgeDebugState(null, Collections.emptyList());
        CountDownLatch entered = new CountDownLatch(1);
        CompletableFuture<Throwable> observed = new CompletableFuture<>();
        Thread waiter = new Thread(() -> {
            entered.countDown();
            try {
                operation.accept(state);
                observed.complete(new AssertionError("operation unexpectedly succeeded"));
            } catch (Throwable error) {
                observed.complete(error);
            }
        });
        waiter.setDaemon(true);
        waiter.start();
        assertTrue(entered.await(2, TimeUnit.SECONDS));
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (waiter.getState() != Thread.State.TIMED_WAITING && System.nanoTime() < deadline) {
            Thread.yield();
        }
        assertEquals("control must actually block before failure", Thread.State.TIMED_WAITING, waiter.getState());
        IllegalStateException original = new IllegalStateException("unconsumed combat damage witness");
        state.fail(original);
        try {
            assertSame(original, rootCause(observed.get(2, TimeUnit.SECONDS)));
        } finally {
            waiter.interrupt();
            waiter.join(2000);
        }
        assertFalse("failed bridge leaves no waiting thread", waiter.isAlive());
    }

    @Test
    public void failedGameWakesCheckpoint() throws Exception {
        assertFailureWakes(BridgeDebugState::checkpoint);
    }

    @Test
    public void failedGameWakesDecisionResult() throws Exception {
        assertFailureWakes(BridgeDebugState::decide);
    }

    @Test
    public void failedGameWakesInitialization() throws Exception {
        assertFailureWakes(BridgeDebugState::initializeOnGameThread);
    }

    @Test
    public void failedGameWakesBlockedDamageProducer() throws Exception {
        assertFailureWakes(state -> {
            state.submitDamagePlan(BridgeTransport.JSON.createObjectNode());
            state.submitDamagePlan(BridgeTransport.JSON.createObjectNode());
        });
    }

    @Test
    public void originalFailureSurvivesRepeatedFailureAndRejectsNewWork() {
        BridgeDebugState state = new BridgeDebugState(null, Collections.emptyList());
        IllegalStateException original = new IllegalStateException("first game failure");
        state.fail(original);
        state.fail(new IllegalStateException("later cleanup failure"));
        try {
            state.replay(BridgeTransport.JSON.createObjectNode());
            fail("new replay must fail");
        } catch (IllegalStateException observed) {
            assertSame(original, rootCause(observed));
        }
    }
}
