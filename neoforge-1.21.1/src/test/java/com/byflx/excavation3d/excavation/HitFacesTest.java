package com.byflx.excavation3d.excavation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class HitFacesTest {
    private static final AABB CUBE = new AABB(10, 64, -3, 11, 65, -2);
    /** Above and south of the block. */
    private static final Vec3 EYE = new Vec3(10.5, 66.6, 1.0);

    @Test
    void visibilityFollowsTheEyeSideOfEachFace() {
        assertTrue(HitFaces.visibleFrom(Direction.UP, CUBE, EYE));
        assertTrue(HitFaces.visibleFrom(Direction.SOUTH, CUBE, EYE));
        assertFalse(HitFaces.visibleFrom(Direction.DOWN, CUBE, EYE));
        assertFalse(HitFaces.visibleFrom(Direction.NORTH, CUBE, EYE));
        assertFalse(HitFaces.visibleFrom(Direction.EAST, CUBE, EYE));
        assertFalse(HitFaces.visibleFrom(Direction.WEST, CUBE, EYE));
    }

    @Test
    void serverRayWinsOverTheClientsFace() {
        // A modified client claims another visible face; the server's own ray decides.
        assertEquals(Direction.UP, HitFaces.choose(Direction.SOUTH, Direction.UP, CUBE, EYE));
    }

    @Test
    void forgedFaceTheEyeIsBehindIsRejected() {
        // The server ray missed (stale view direction) and the client claims the hidden back face.
        Direction chosen = HitFaces.choose(Direction.NORTH, null, CUBE, EYE);
        assertTrue(HitFaces.visibleFrom(chosen, CUBE, EYE));
        assertEquals(Direction.SOUTH, chosen);
        assertEquals(Direction.SOUTH, HitFaces.choose(Direction.DOWN, null, CUBE, EYE));
    }

    @Test
    void visibleClientFaceIsUsedWhenTheServerRayMisses() {
        assertEquals(Direction.UP, HitFaces.choose(Direction.UP, null, CUBE, EYE));
    }

    @Test
    void partialBlockUsesItsOutline() {
        AABB slab = new AABB(0, 0, 0, 1, 0.5, 1);
        Vec3 eye = new Vec3(0.5, 0.8, 3.0);
        assertTrue(HitFaces.visibleFrom(Direction.UP, slab, eye));
        assertFalse(HitFaces.visibleFrom(Direction.UP, new AABB(0, 0, 0, 1, 1, 1), eye));
        assertEquals(Direction.UP, HitFaces.choose(Direction.UP, null, slab, eye));
    }

    @Test
    void chosenFaceIsAlwaysVisibleFromOutside() {
        AABB slab = new AABB(0, 0, 0, 1, 0.5, 1);
        for (AABB box : new AABB[] {new AABB(0, 0, 0, 1, 1, 1), slab}) {
            for (double x = -2.25; x <= 3; x += 0.5) {
                for (double y = -2.25; y <= 3; y += 0.5) {
                    for (double z = -2.25; z <= 3; z += 0.5) {
                        Vec3 eye = new Vec3(x, y, z);
                        if (box.contains(eye)) {
                            continue;
                        }
                        for (Direction claimed : Direction.values()) {
                            Direction chosen = HitFaces.choose(claimed, null, box, eye);
                            assertTrue(HitFaces.visibleFrom(chosen, box, eye), () -> chosen + " from " + eye);
                            if (HitFaces.visibleFrom(claimed, box, eye)) {
                                assertEquals(claimed, chosen);
                            }
                        }
                    }
                }
            }
        }
    }
}
