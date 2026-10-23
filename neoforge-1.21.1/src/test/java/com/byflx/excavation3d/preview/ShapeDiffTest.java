package com.byflx.excavation3d.preview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.byflx.excavation3d.shape.Cell;
import com.byflx.excavation3d.shape.GeneratedShape;
import com.byflx.excavation3d.shape.ShapeConfig;
import com.byflx.excavation3d.shape.ShapeGenerator;
import com.byflx.excavation3d.shape.ShapeLimits;
import com.byflx.excavation3d.shape.ShapeParams;
import org.junit.jupiter.api.Test;

class ShapeDiffTest {
    private static GeneratedShape cuboid(int w, int h, int d) {
        ShapeGenerator.Result result = ShapeGenerator.generate(ShapeConfig.of(new ShapeParams.Cuboid(w, h, d)),
                ShapeLimits.DEFAULT);
        return ((ShapeGenerator.Valid) result).shape();
    }

    @Test
    void identicalShapesHaveNoDiff() {
        assertTrue(ShapeDiff.between(cuboid(3, 3, 1), cuboid(3, 3, 1)).isEmpty());
    }

    @Test
    void widthChangeAddsOrRemovesTheSideColumns() {
        ShapeDiff grow = ShapeDiff.between(cuboid(3, 3, 1), cuboid(5, 3, 1));
        assertEquals(6, grow.added().size());
        assertEquals(0, grow.removed().size());
        ShapeDiff shrink = ShapeDiff.between(cuboid(5, 3, 1), cuboid(3, 3, 1));
        assertEquals(0, shrink.added().size());
        assertEquals(6, shrink.removed().size());
    }

    @Test
    void depthChangeAddsAFullBackLayer() {
        ShapeDiff diff = ShapeDiff.between(cuboid(3, 3, 1), cuboid(3, 3, 2));
        assertEquals(9, diff.added().size());
        assertTrue(diff.added().stream().allMatch(c -> c.z() == 1));
        assertTrue(diff.removed().isEmpty());
    }

    @Test
    void evenSizeKeepsTheAnchorFixed() {
        // Width 3 → 4 keeps the anchor column; the new column appears on one side only.
        ShapeDiff diff = ShapeDiff.between(cuboid(3, 3, 1), cuboid(4, 3, 1));
        assertEquals(3, diff.added().size());
        assertTrue(diff.added().stream().allMatch(c -> c.x() == 2));
        assertTrue(diff.removed().isEmpty());
        assertTrue(diff.added().contains(new Cell(2, 0, 0)));
    }

    @Test
    void boundsWithoutRemovedCellsAreTheShapeBox() {
        GeneratedShape shown = cuboid(5, 3, 2);
        assertEquals(new ShapeDiff.Bounds(0, 0, 0, 5, 3, 2), ShapeDiff.NONE.bounds(shown));
        assertEquals(new ShapeDiff.Bounds(0, 0, 0, 5, 3, 2), ShapeDiff.between(cuboid(3, 3, 1), shown).bounds(shown));
    }

    @Test
    void shrinkBoundsCoverTheRemovedGhosts() {
        // 3×5×1 → 3×3×1: the removed rows stay visible one row below and one row above the shape.
        GeneratedShape shown = cuboid(3, 3, 1);
        ShapeDiff shrink = ShapeDiff.between(cuboid(3, 5, 1), shown);
        assertEquals(new ShapeDiff.Bounds(0, -1, 0, 3, 4, 1), shrink.bounds(shown));
    }

}
