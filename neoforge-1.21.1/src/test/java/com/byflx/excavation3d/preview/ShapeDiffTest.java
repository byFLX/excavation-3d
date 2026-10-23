package com.byflx.excavation3d.preview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.byflx.excavation3d.shape.Cell;
import com.byflx.excavation3d.shape.GeneratedShape;
import com.byflx.excavation3d.shape.ShapeConfig;
import com.byflx.excavation3d.shape.ShapeGenerator;
import com.byflx.excavation3d.shape.ShapeLimits;
import com.byflx.excavation3d.shape.ShapeParams;
import java.util.List;
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

    @Test
    void frontViewHeightLabelKeepsClearOfTheRemovedGhostRow() {
        // Straight front view as in the editor: screen x = cx + s·(x − w/2), screen y = cy − s·(y − h/2).
        GeneratedShape shown = cuboid(3, 3, 1);
        ShapeDiff shrink = ShapeDiff.between(cuboid(3, 5, 1), shown);
        LabelPlacement.Rect panel = new LabelPlacement.Rect(0, 0, 320, 240);
        int cx = 160;
        int cy = 120;
        for (int s = 6; s <= 30; s++) {
            LabelPlacement.Rect ghostRect = project(shrink.bounds(shown), shown, s, cx, cy);
            LabelPlacement.Rect upperGhostRow = project(new ShapeDiff.Bounds(0, 3, 0, 3, 4, 1), shown, s, cx, cy);
            // The height label wants the axis end at (0, h + 0.9), which lies on the upper ghost row.
            LabelPlacement.Label height = new LabelPlacement.Label(Math.round(cx - s * 1.5F),
                    Math.round(cy - s * (3.9F - 1.5F)), 45, 9);
            LabelPlacement.Layout layout = LabelPlacement.layout(List.of(height), ghostRect, panel);
            LabelPlacement.Rect label = layout.labels().get(0);
            assertFalse(label.intersects(upperGhostRow), s + ": " + label + " covers " + upperGhostRow);
            assertFalse(label.intersects(ghostRect), s + ": " + label + " covers " + ghostRect);
            assertTrue(label.inside(panel), s + ": " + label);
        }
    }

    private static LabelPlacement.Rect project(ShapeDiff.Bounds box, GeneratedShape shown, int s, int cx, int cy) {
        float halfW = shown.width() / 2.0F;
        float halfH = shown.height() / 2.0F;
        return new LabelPlacement.Rect((int) Math.floor(cx + s * (box.minX() - halfW)),
                (int) Math.floor(cy - s * (box.maxY() - halfH)), (int) Math.ceil(cx + s * (box.maxX() - halfW)),
                (int) Math.ceil(cy - s * (box.minY() - halfH)));
    }
}
