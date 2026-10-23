package com.byflx.excavation3d.preview;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StableTargetTest {
    @Test void alternatingWallFloorAndBriefMissesKeepTheLastStableTarget() {
        var target = new StableTarget<String>();
        assertEquals("wall", target.select("wall", 0, true));
        for (int tick = 1; tick < 80; tick++) {
            String hit = switch (tick % 3) { case 0 -> "wall"; case 1 -> "floor"; default -> null; };
            assertEquals("wall", target.select(hit, tick, true));
        }
    }

    @Test void aPersistentNearbyTargetOrMissTakesOverAfterThreeTicks() {
        var target = new StableTarget<String>();
        target.select("wall", 0, true);
        for (int tick = 10; tick < 13; tick++) assertEquals("wall", target.select("floor", tick, true));
        assertEquals("floor", target.select("floor", 13, true));
        for (int tick = 20; tick < 23; tick++) assertEquals("floor", target.select(null, tick, true));
        assertNull(target.select(null, 23, true));
        assertEquals("wall", target.select("wall", 24, true));
    }

    @Test void manyRenderFramesCannotConsumeTheTickGracePeriod() {
        var target = new StableTarget<String>();
        target.select("wall", 100, true);
        for (int frame = 0; frame < 300; frame++) assertEquals("wall", target.select(null, 101, true));
        assertEquals("wall", target.select(null, 103, true));
        assertNull(target.select(null, 104, true));
    }

    @Test void returningToTheStableTargetCancelsThePendingSwitch() {
        var target = new StableTarget<String>();
        target.select("wall", 0, true);
        target.select("floor", 1, true);
        target.select("wall", 3, true);
        assertEquals("wall", target.select("floor", 4, true));
        assertEquals("wall", target.select("floor", 6, true));
        assertEquals("floor", target.select("floor", 7, true));
    }

    @Test void deliberateDistantTargetsAndContextResetsAreImmediate() {
        var target = new StableTarget<String>();
        target.select("wall", 0, true);
        assertEquals("far wall", target.select("far wall", 1, false));
        target.select("floor", 2, true);
        target.reset(); // Tool, config, world, preview toggle, or removed/unloaded start.
        assertNull(target.current());
        assertNull(target.select(null, 0, true));
        assertEquals("floor", target.select("floor", 0, true));
        assertNull(target.select(null, 1, false));
    }
}
