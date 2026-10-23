package com.byflx.excavation3d.shape;

import static com.byflx.excavation3d.shape.TestShapes.localOffsets;
import static com.byflx.excavation3d.shape.TestShapes.valid;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class ShapeTransformTest {
    private static List<ShapeMask> samples() {
        boolean[][][] cells = new boolean[3][2][2];
        cells[0][0][0] = true;
        cells[2][1][0] = true;
        cells[1][1][1] = true;
        return List.of(
                valid(new ShapeParams.Plus(4, 3, 2, 1)).mask(),
                valid(new ShapeParams.Stair(2, 3, 1, 2, true)).mask(),
                valid(new ShapeParams.Corridor(3, 2, 4, 1, 1, false)).mask(),
                ShapeMask.of(cells, new Cell(0, 0, 0)));
    }

    @Test
    void fourQuarterTurnsAreIdentity() {
        ShapeTransform quarter = new ShapeTransform(1, false);
        for (ShapeMask mask : samples()) {
            ShapeMask turned = mask;
            for (int i = 0; i < 4; i++) {
                turned = quarter.apply(turned);
            }
            assertEquals(mask, turned);
            assertEquals(mask.anchor(), turned.anchor());
        }
    }

    @Test
    void quarterTurnSwapsWidthAndHeight() {
        ShapeMask line = valid(new ShapeParams.Cuboid(3, 1, 1)).mask();
        ShapeMask turned = new ShapeTransform(1, false).apply(line);
        assertEquals(List.of(1, 3, 1), List.of(turned.width(), turned.height(), turned.depth()));
        assertEquals(3, turned.count());

        GeneratedShape generated = valid(ShapeConfig.of(new ShapeParams.Cuboid(3, 1, 1), new ShapeTransform(1, false)),
                ShapeLimits.DEFAULT);
        assertEquals(turned, generated.mask());
    }

    @Test
    void quarterTurnIsClockwiseLookingIntoTheBlock() {
        // Left cell of a 2×1 row moves to the top of the 1×2 column.
        boolean[][][] cells = new boolean[2][1][1];
        cells[0][0][0] = true;
        ShapeMask mask = ShapeMask.of(cells, new Cell(0, 0, 0));
        ShapeMask turned = new ShapeTransform(1, false).apply(mask);
        assertEquals(List.of(new Cell(0, 1, 0)), turned.positions());
        assertEquals(new Cell(0, 1, 0), turned.anchor());
    }

    @Test
    void mirroringTwiceIsIdentity() {
        ShapeTransform mirror = new ShapeTransform(0, true);
        for (ShapeMask mask : samples()) {
            assertEquals(mask, mirror.apply(mirror.apply(mask)));
        }
    }

    @Test
    void mirroringEvenPlusChangesOffsets() {
        ShapeMask plus = valid(new ShapeParams.Plus(4, 4, 1, 1)).mask();
        ShapeMask mirrored = new ShapeTransform(0, true).apply(plus);
        assertEquals(new Cell(2, 1, 0), mirrored.anchor());
        assertNotEquals(localOffsets(plus), localOffsets(mirrored));
        assertEquals(plus.count(), mirrored.count());
    }

    @Test
    void everyTransformKeepsCountAndDepth() {
        for (ShapeMask mask : samples()) {
            for (ShapeTransform transform : ShapeTransform.all()) {
                ShapeMask result = transform.apply(mask);
                assertEquals(mask.count(), result.count(), transform.toString());
                assertEquals(mask.depth(), result.depth());
                assertLayerCounts(mask, result);
            }
        }
    }

    @Test
    void allListsEightDistinctTransforms() {
        assertEquals(8, new HashSet<>(ShapeTransform.all()).size());
        ShapeMask sample = samples().get(0);
        assertSame(sample, ShapeTransform.IDENTITY.apply(sample));
        assertEquals(new ShapeTransform(0, false), new ShapeTransform(3, false).rotatedClockwise());
        assertEquals(new ShapeTransform(2, true), new ShapeTransform(2, false).toggledMirror());
    }

    @Test
    void invalidRotationIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ShapeTransform(4, false));
        assertThrows(IllegalArgumentException.class, () -> new ShapeTransform(-1, true));
    }

    /** z never changes, so each depth layer keeps its cell count. */
    private static void assertLayerCounts(ShapeMask before, ShapeMask after) {
        for (int z = 0; z < before.depth(); z++) {
            int layer = z;
            assertEquals(before.positions().stream().filter(c -> c.z() == layer).count(),
                    after.positions().stream().filter(c -> c.z() == layer).count());
        }
    }
}
