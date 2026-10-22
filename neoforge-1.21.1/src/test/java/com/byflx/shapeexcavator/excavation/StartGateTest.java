package com.byflx.shapeexcavator.excavation;

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
    void cancelledBreakFollowedByAnotherPlayersRemovalStartsNothing() {
        StartGate<String> gate = new StartGate<>();
        // A's break event reached this listener, then a later listener cancelled it: no confirm.
        gate.attempt(A, POS, "a");
        // B removes the same block within the same tick through B's own successful break.
        gate.attempt(B, POS, "b");
        gate.confirm(B, POS);

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
        assertTrue(gate.confirm(A, POS));
        assertTrue(gate.drain((pos, payload) -> false).isEmpty());

        gate.attempt(A, POS, "a");
        gate.confirm(A, POS);
        assertEquals(Map.of(A, "a"), gate.drain((pos, payload) -> pos.equals(POS)));
    }

    @Test
    void confirmationMustMatchPlayerAndPosition() {
        StartGate<String> gate = new StartGate<>();
        assertFalse(gate.confirm(A, POS));
        gate.attempt(A, POS, "a");
        assertFalse(gate.confirm(A, POS.above()));
        assertFalse(gate.confirm(B, POS));
        assertTrue(gate.drain((pos, payload) -> true).isEmpty());
    }

    @Test
    void newAttemptReplacesTheOldOneWithoutItsConfirmation() {
        StartGate<String> gate = new StartGate<>();
        gate.attempt(A, POS, "first");
        gate.confirm(A, POS);
        gate.attempt(A, POS.east(), "second");
        assertTrue(gate.drain((pos, payload) -> true).isEmpty());
    }

    @Test
    void drainForgetsEverythingSoAStartHappensAtMostOnce() {
        StartGate<String> gate = new StartGate<>();
        gate.attempt(A, POS, "a");
        gate.confirm(A, POS);
        gate.attempt(B, POS.below(), "b");
        assertEquals(List.of("a"), List.copyOf(gate.drain((pos, payload) -> true).values()));
        assertFalse(gate.has(A));
        assertFalse(gate.has(B));
        assertTrue(gate.drain((pos, payload) -> true).isEmpty());
    }

    @Test
    void forgetAndClearDropAttempts() {
        StartGate<String> gate = new StartGate<>();
        gate.attempt(A, POS, "a");
        gate.confirm(A, POS);
        gate.forget(A);
        assertTrue(gate.drain((pos, payload) -> true).isEmpty());

        gate.attempt(A, POS, "a");
        gate.attempt(B, POS, "b");
        gate.confirm(A, POS);
        gate.confirm(B, POS);
        gate.clear();
        assertTrue(gate.drain((pos, payload) -> true).isEmpty());
    }

    @Test
    void storedPositionIsImmutable() {
        StartGate<String> gate = new StartGate<>();
        BlockPos.MutableBlockPos mutable = POS.mutable();
        gate.attempt(A, mutable, "a");
        mutable.move(0, 5, 0);
        assertTrue(gate.confirm(A, POS));
    }
}
