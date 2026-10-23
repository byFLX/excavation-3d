package com.byflx.excavation3d.excavation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ExtraBreakTest {
    @Test
    void refusedRemovalIsNotCountedAsMined() {
        // destroyBlock reported success, but onDestroyedByPlayer kept the block in place.
        assertEquals(ExtraBreak.REFUSED, ExtraBreak.of(true, false));
    }

    @Test
    void onlyAConfirmedRemovalIsMined() {
        assertEquals(ExtraBreak.MINED, ExtraBreak.of(true, true));
    }

    @Test
    void cancelledBreakIsProtectedEvenIfTheBlockIsGone() {
        // The player's break never ran; whatever removed the block, it was not this operation.
        assertEquals(ExtraBreak.PROTECTED, ExtraBreak.of(false, false));
        assertEquals(ExtraBreak.PROTECTED, ExtraBreak.of(false, true));
    }
}
