package com.byflx.shapeexcavator.shape;

import static com.byflx.shapeexcavator.shape.TestShapes.codes;
import static com.byflx.shapeexcavator.shape.TestShapes.issues;
import static com.byflx.shapeexcavator.shape.TestShapes.valid;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.byflx.shapeexcavator.shape.ShapeIssue.Code;
import java.util.BitSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

class CustomMaskTest {
    private static ShapeMask ringWithoutCentre() {
        boolean[][][] cells = new boolean[3][3][1];
        for (int x = 0; x < 3; x++) {
            for (int y = 0; y < 3; y++) {
                cells[x][y][0] = !(x == 1 && y == 1);
            }
        }
        return ShapeMask.of(cells, new Cell(1, 0, 0));
    }

    @Test
    void deselectedCellsAreNeverSelected() {
        GeneratedShape shape = valid(new ShapeParams.Custom(ringWithoutCentre()));
        assertEquals(8, shape.count());
        assertFalse(shape.positions().contains(new Cell(1, 1, 0)));
        List<Offset> offsets = shape.worldOffsets(new Orientation(Direction.EAST, Direction.UP, Direction.NORTH));
        assertEquals(8, offsets.size());
        assertFalse(offsets.contains(new Offset(0, 1, 0)));
        assertTrue(offsets.contains(Offset.ZERO));
    }

    @Test
    void emptyMaskIsRejected() {
        assertEquals(List.of(ShapeIssue.of(Code.EMPTY_MASK, "mask")),
                issues(new ShapeParams.Custom(ShapeMask.empty(3, 3, 1, new Cell(1, 1, 0)))));
    }

    @Test
    void unselectedAnchorIsRejected() {
        ShapeMask mask = ShapeMask.of(ringWithoutCentre().width(), 3, 1, ringWithoutCentre().toBitSet(), new Cell(1, 1, 0));
        assertEquals(List.of(ShapeIssue.of(Code.ANCHOR_NOT_SELECTED, "anchor", 1, 1, 0)),
                issues(new ShapeParams.Custom(mask)));
    }

    @Test
    void anchorMustBeInFrontmostOccupiedLayer() {
        boolean[][][] cells = new boolean[1][1][3];
        cells[0][0][1] = true;
        cells[0][0][2] = true;
        assertEquals(List.of(ShapeIssue.of(Code.ANCHOR_NOT_IN_FRONT_LAYER, "anchor", 2, 1)),
                issues(new ShapeParams.Custom(ShapeMask.of(cells, new Cell(0, 0, 2)))));
        // An empty front plane is fine: the anchor sits in the frontmost occupied layer.
        GeneratedShape shape = valid(new ShapeParams.Custom(ShapeMask.of(cells, new Cell(0, 0, 1))));
        assertEquals(2, shape.count());
    }

    @Test
    void customAboveLoweredLimitIsRejected() {
        ShapeMask wide = ShapeMask.full(20, 1, 1, new Cell(0, 0, 0));
        assertEquals(Set.of(Code.AXIS_TOO_LARGE),
                codes(issues(ShapeConfig.of(new ShapeParams.Custom(wide)), new ShapeLimits(16, 512))));
    }

    @Test
    void inputsAreCopiedAndOutputsAreImmutable() {
        BitSet bits = new BitSet();
        bits.set(0);
        bits.set(4);
        ShapeMask fromBits = ShapeMask.of(3, 3, 1, bits, new Cell(0, 0, 0));
        bits.clear();
        assertEquals(2, fromBits.count());

        boolean[][][] cells = new boolean[2][1][1];
        cells[0][0][0] = true;
        ShapeMask fromArray = ShapeMask.of(cells, new Cell(0, 0, 0));
        cells[1][0][0] = true;
        cells[0][0][0] = false;
        assertEquals(List.of(new Cell(0, 0, 0)), fromArray.positions());

        BitSet copy = fromBits.toBitSet();
        copy.set(8);
        assertEquals(2, fromBits.count());
        assertFalse(fromBits.contains(2, 2, 0));

        assertThrows(UnsupportedOperationException.class, () -> fromBits.positions().add(new Cell(1, 1, 0)));
        assertThrows(UnsupportedOperationException.class, () -> fromBits.positions().clear());
        GeneratedShape shape = valid(new ShapeParams.Custom(fromBits));
        assertThrows(UnsupportedOperationException.class,
                () -> shape.worldOffsets(new Orientation(Direction.EAST, Direction.UP, Direction.NORTH)).clear());
    }

    @Test
    void equalityIsByValue() {
        ShapeMask a = ringWithoutCentre();
        ShapeMask b = ShapeMask.of(3, 3, 1, a.toBitSet(), new Cell(1, 0, 0));
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, ShapeMask.of(3, 3, 1, a.toBitSet(), new Cell(0, 0, 0)));
        assertNotEquals(a, ShapeMask.full(3, 3, 1, new Cell(1, 0, 0)));
        assertEquals(new ShapeParams.Custom(a), new ShapeParams.Custom(b));
    }

    @Test
    void structuralViolationsAreRejectedAtConstruction() {
        assertThrows(IllegalArgumentException.class, () -> ShapeMask.empty(0, 1, 1, new Cell(0, 0, 0)));
        assertThrows(IllegalArgumentException.class, () -> ShapeMask.empty(33, 1, 1, new Cell(0, 0, 0)));
        assertThrows(IllegalArgumentException.class, () -> ShapeMask.full(2, 2, 2, new Cell(2, 0, 0)));
        assertThrows(IllegalArgumentException.class, () -> ShapeMask.full(2, 2, 2, new Cell(0, -1, 0)));
        BitSet outside = new BitSet();
        outside.set(9);
        assertThrows(IllegalArgumentException.class, () -> ShapeMask.of(3, 3, 1, outside, new Cell(0, 0, 0)));
        assertThrows(IllegalArgumentException.class,
                () -> ShapeMask.of(new boolean[][][] {{{true}}, {{true, false}}}, new Cell(0, 0, 0)));
    }
}
