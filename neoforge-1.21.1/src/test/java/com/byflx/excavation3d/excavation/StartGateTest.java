package com.byflx.excavation3d.excavation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

class StartGateTest {
    private static final UUID A = new UUID(0, 1);
    private static final UUID B = new UUID(0, 2);
    private static final BlockPos POS = new BlockPos(10, 64, -3);

    @Test
    void refusedRemovalFollowedByAnotherPlayersRemovalStartsNothing() {
        StartGate<String> gate = new StartGate<>();
        // A's break event passed, but the block refused A's removal: no drops for A, no confirmation.
        gate.attempt(A, POS, "a");
        // B (no shape tool) removes the same block in the same tick; B's drops name B as breaker.
        assertFalse(gate.confirmRemoved(B, POS));

        assertTrue(gate.drain((pos, payload) -> true).isEmpty());
    }

    @Test
    void anotherPlayersSuccessfulBreakConfirmsOnlyThatPlayer() {
        StartGate<String> gate = new StartGate<>();
        // A's break event passed and a later listener cancelled it: no confirmation.
        gate.attempt(A, POS, "a");
        // B removes the same block within the same tick through B's own successful break.
        gate.attempt(B, POS, "b");
        gate.confirmRemoved(B, POS);

        Map<UUID, String> started = gate.drain((pos, payload) -> true);
        assertEquals(Map.of(B, "b"), started);
    }

    @Test
    void unconfirmedAttemptNeverStartsEvenIfTheBlockIsGone() {
        StartGate<String> gate = new StartGate<>();
        gate.attempt(A, POS, "a");
        assertTrue(gate.drain((pos, payload) -> true).isEmpty());
    }

    @Test
    void confirmedAttemptStartsOnlyIfTheStartBlockIsGone() {
        StartGate<String> gate = new StartGate<>();
        gate.attempt(A, POS, "a");
        assertTrue(gate.confirmRemoved(A, POS));
        assertTrue(gate.drain((pos, payload) -> false).isEmpty());

        gate.attempt(A, POS, "a");
        gate.confirmRemoved(A, POS);
        assertEquals(Map.of(A, "a"), gate.drain((pos, payload) -> pos.equals(POS)));
    }

    @Test
    void bypassArrivingAfterTheBreakInTheSameTickStartsNothing() {
        StartGate<String> gate = new StartGate<>();
        // The mining action was processed before the single-block key update.
        gate.attempt(A, POS, "a");
        gate.confirmRemoved(A, POS);
        gate.setBypass(A, true);

        assertTrue(gate.drain((pos, payload) -> true).isEmpty());
    }

    @Test
    void heldBypassBlocksAttemptsUntilReleased() {
        StartGate<String> gate = new StartGate<>();
        gate.setBypass(A, true);
        assertTrue(gate.bypassActive(A));
        assertFalse(gate.attempt(A, POS, "a"));
        assertFalse(gate.confirmRemoved(A, POS));
        assertTrue(gate.drain((pos, payload) -> true).isEmpty());

        // Another player's key does not affect A, and releasing it allows starts again.
        gate.setBypass(B, true);
        gate.setBypass(A, false);
        assertFalse(gate.bypassActive(A));
        assertTrue(gate.attempt(A, POS, "a"));
        gate.confirmRemoved(A, POS);
        assertEquals(Map.of(A, "a"), gate.drain((pos, payload) -> true));
    }

    @Test
    void confirmationMustMatchPlayerAndPosition() {
        StartGate<String> gate = new StartGate<>();
        assertFalse(gate.confirmRemoved(A, POS));
        gate.attempt(A, POS, "a");
        assertFalse(gate.confirmRemoved(A, POS.above()));
        assertFalse(gate.confirmRemoved(B, POS));
        assertTrue(gate.drain((pos, payload) -> true).isEmpty());
    }

    @Test
    void newAttemptReplacesTheOldOneWithoutItsConfirmation() {
        StartGate<String> gate = new StartGate<>();
        gate.attempt(A, POS, "first");
        gate.confirmRemoved(A, POS);
        gate.attempt(A, POS.east(), "second");
        assertTrue(gate.drain((pos, payload) -> true).isEmpty());
    }

    @Test
    void drainForgetsEverythingSoAStartHappensAtMostOnce() {
        StartGate<String> gate = new StartGate<>();
        gate.attempt(A, POS, "a");
        gate.confirmRemoved(A, POS);
        gate.attempt(B, POS.below(), "b");
        assertEquals(List.of("a"), List.copyOf(gate.drain((pos, payload) -> true).values()));
        assertFalse(gate.has(A));
        assertFalse(gate.has(B));
        assertTrue(gate.drain((pos, payload) -> true).isEmpty());
    }

    @Test
    void forgetRemovePlayerAndClearDropState() {
        StartGate<String> gate = new StartGate<>();
        gate.attempt(A, POS, "a");
        gate.confirmRemoved(A, POS);
        gate.forget(A);
        assertTrue(gate.drain((pos, payload) -> true).isEmpty());

        // forget (death, dimension change) keeps the held key; removePlayer (logout) does not.
        gate.setBypass(A, true);
        gate.forget(A);
        assertTrue(gate.bypassActive(A));
        gate.removePlayer(A);
        assertFalse(gate.bypassActive(A));

        gate.attempt(A, POS, "a");
        gate.attempt(B, POS, "b");
        gate.confirmRemoved(A, POS);
        gate.confirmRemoved(B, POS);
        gate.setBypass(B, true);
        gate.clear();
        assertFalse(gate.bypassActive(B));
        assertTrue(gate.drain((pos, payload) -> true).isEmpty());
    }

    @Test
    void storedPositionIsImmutable() {
        StartGate<String> gate = new StartGate<>();
        BlockPos.MutableBlockPos mutable = POS.mutable();
        gate.attempt(A, mutable, "a");
        mutable.move(0, 5, 0);
        assertTrue(gate.confirmRemoved(A, POS));
    }
}
