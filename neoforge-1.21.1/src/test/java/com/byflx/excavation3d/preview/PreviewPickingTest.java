package com.byflx.excavation3d.preview;

import com.byflx.excavation3d.shape.Cell;
import com.byflx.excavation3d.shape.ShapeMask;
import java.util.BitSet;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PreviewPickingTest {
    @Test void frontRayFindsFirstSelectedCellAndItsFace() {
        ShapeMask mask = ShapeMask.full(3, 3, 3, new Cell(1, 1, 0));
        var camera = new PreviewPicking.Camera(100, 100, 20, 0, 0, 3, 3, 3);
        var hit = PreviewPicking.pick(camera, 100, 100, mask, 2).orElseThrow();
        assertEquals(new Cell(1, 1, 0), hit.cell());
        assertEquals(PreviewPicking.Face.FRONT, hit.face());
        assertTrue(hit.selected());
        assertTrue(PreviewPicking.addTarget(hit, mask).isEmpty());
        assertTrue(PreviewPicking.pick(camera, 1000, 1000, mask, 0).isEmpty());
    }

    @Test void slabsIdentifyAllSixFacesAndParallelMisses() {
        var origins = new PreviewPicking.Vec[] {
                new PreviewPicking.Vec(-2, .5, .5), new PreviewPicking.Vec(3, .5, .5),
                new PreviewPicking.Vec(.5, -2, .5), new PreviewPicking.Vec(.5, 3, .5),
                new PreviewPicking.Vec(.5, .5, -2), new PreviewPicking.Vec(.5, .5, 3)};
        var directions = new PreviewPicking.Vec[] {
                new PreviewPicking.Vec(1, 0, 0), new PreviewPicking.Vec(-1, 0, 0),
                new PreviewPicking.Vec(0, 1, 0), new PreviewPicking.Vec(0, -1, 0),
                new PreviewPicking.Vec(0, 0, 1), new PreviewPicking.Vec(0, 0, -1)};
        for (int i = 0; i < 6; i++) {
            var hit = PreviewPicking.intersect(new PreviewPicking.Ray(origins[i], directions[i]), new Cell(0, 0, 0), true);
            assertNotNull(hit);
            assertEquals(PreviewPicking.Face.values()[i], hit.face());
            assertEquals(2, hit.distance(), 1e-8);
        }
        assertNull(PreviewPicking.intersect(new PreviewPicking.Ray(new PreviewPicking.Vec(4, 4, -3),
                new PreviewPicking.Vec(0, 0, 1)), new Cell(0, 0, 0), true));
        assertNull(PreviewPicking.intersect(new PreviewPicking.Ray(new PreviewPicking.Vec(.5, .5, -3),
                new PreviewPicking.Vec(0, 0, -1)), new Cell(0, 0, 0), true));
    }

    @Test void projectionAndPickingShareRotationScaleAndCenterAtAnyCameraAngle() {
        ShapeMask mask = ShapeMask.full(1, 1, 1, new Cell(0, 0, 0));
        for (double yaw : new double[] {-170, -90, -35, 0, 90, 170}) {
            for (double elevation : new double[] {-90, -25, 0, 25, 90}) {
                var camera = new PreviewPicking.Camera(173, 94, 37, yaw, elevation, 1, 1, 1);
                var point = camera.project(new PreviewPicking.Vec(.5, .5, .5));
                var hit = PreviewPicking.pick(camera, point.x(), point.y(), mask, 0).orElseThrow();
                assertEquals(new Cell(0, 0, 0), hit.cell());
                float[] m = camera.matrix();
                assertEquals(point.x(), m[0] * .5 + m[4] * .5 + m[8] * .5 + m[12], 1e-4);
                assertEquals(point.y(), m[1] * .5 + m[5] * .5 + m[9] * .5 + m[13], 1e-4);
            }
        }
    }

    @Test void selectedFaceAddsNeighbourBeforeGhostsAndEmptyLayerCanBePainted() {
        BitSet bits = new BitSet(); bits.set((1 * 3 + 1) * 3 + 1);
        ShapeMask mask = ShapeMask.of(3, 3, 3, bits, new Cell(1, 1, 1));
        var camera = new PreviewPicking.Camera(100, 100, 20, 0, 0, 3, 3, 3);
        var hit = PreviewPicking.pick(camera, 100, 100, mask, 0).orElseThrow();
        assertTrue(hit.selected());
        assertEquals(new Cell(1, 1, 0), PreviewPicking.addTarget(hit, mask).orElseThrow());
        ShapeMask empty = ShapeMask.of(3, 3, 3, new BitSet(), new Cell(1, 1, 0));
        var ghost = PreviewPicking.pick(camera, 100, 100, empty, 2).orElseThrow();
        assertFalse(ghost.selected());
        assertEquals(new Cell(1, 1, 2), PreviewPicking.addTarget(ghost, empty).orElseThrow());
    }

    @Test void floorGridAddsBottomCellOutsideTheCurrentGhostLayer() {
        ShapeMask empty = ShapeMask.of(3, 3, 3, new BitSet(), new Cell(1, 1, 0));
        var camera = new PreviewPicking.Camera(100, 100, 20, 0, 90, 3, 3, 3);
        var floor = camera.project(new PreviewPicking.Vec(.5, 0, 2.5));
        var hit = PreviewPicking.pick(camera, floor.x(), floor.y(), empty, 0).orElseThrow();
        assertEquals(new Cell(0, 0, 2), hit.cell());
        assertEquals(PreviewPicking.Face.UP, hit.face());
    }

    @Test void clickHasSmallSlopButDragStartsAtTheThreshold() {
        assertFalse(PreviewPicking.isDrag(10, 10, 12, 12));
        assertTrue(PreviewPicking.isDrag(10, 10, 14, 10));
        assertTrue(PreviewPicking.isDrag(10, 10, 13, 13));
    }

    @Test void templateFacesExposeOutsideNeighboursWhileCustomKeepsItsCanvasBounds() {
        ShapeMask mask = ShapeMask.full(1, 1, 1, new Cell(0, 0, 0));
        var camera = new PreviewPicking.Camera(100, 100, 20, 0, 0, 1, 1, 1);
        var hit = PreviewPicking.pick(camera, 100, 100, mask, 0, true).orElseThrow();
        assertTrue(hit.selected());
        assertEquals(new Cell(0, 0, -1), PreviewPicking.addTarget(hit, mask, true).orElseThrow());
        assertTrue(PreviewPicking.addTarget(hit, mask).isEmpty());
    }

    @Test void templateFloorPickingExposesOnlyOneCellBeyondEachSide() {
        ShapeMask mask = ShapeMask.full(1, 1, 1, new Cell(0, 0, 0));
        var camera = new PreviewPicking.Camera(100, 100, 20, 0, 90, 1, 1, 1);
        for (Cell cell : new Cell[] {new Cell(-1, 0, 0), new Cell(1, 0, 0),
                new Cell(0, 0, -1), new Cell(0, 0, 1)}) {
            var point = camera.project(new PreviewPicking.Vec(cell.x() + .5, 0, cell.z() + .5));
            var floor = PreviewPicking.pick(camera, point.x(), point.y(), mask, 0, true).orElseThrow();
            assertFalse(floor.selected());
            assertEquals(cell, PreviewPicking.addTarget(floor, mask, true).orElseThrow());
            assertTrue(PreviewPicking.pick(camera, point.x(), point.y(), mask, 0).isEmpty());
        }
        var outside = camera.project(new PreviewPicking.Vec(-1.5, 0, .5));
        assertTrue(PreviewPicking.pick(camera, outside.x(), outside.y(), mask, 0, true).isEmpty());
    }
}
