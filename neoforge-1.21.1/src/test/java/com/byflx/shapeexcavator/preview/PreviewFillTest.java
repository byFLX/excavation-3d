package com.byflx.shapeexcavator.preview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.byflx.shapeexcavator.preview.PreviewFill.Face;
import com.byflx.shapeexcavator.preview.PreviewFill.Side;
import com.byflx.shapeexcavator.shape.Offset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PreviewFillTest {
    @Test
    void airExcludedAndUnloadedGetNoFill() {
        Map<Offset, CellCategory> cells = new LinkedHashMap<>();
        cells.put(new Offset(0, 0, 0), CellCategory.AIR);
        cells.put(new Offset(1, 0, 0), CellCategory.EXCLUDED);
        cells.put(new Offset(2, 0, 0), CellCategory.UNLOADED);
        PreviewFill.Result result = PreviewFill.of(cells);
        assertTrue(result.faces().isEmpty());
        assertEquals(0, result.count());
        assertEquals(Optional.empty(), result.start());
    }

    @Test
    void flatWallGetsOnlyItsOuterFaces() {
        Map<Offset, CellCategory> cells = new LinkedHashMap<>();
        for (int x = 0; x < 3; x++) {
            for (int y = 0; y < 3; y++) {
                cells.put(new Offset(x, y, 0), CellCategory.PRESENT);
            }
        }
        PreviewFill.Result result = PreviewFill.of(cells);
        // 9 front + 9 back + 3 on each of the four sides; nothing between two mined cells.
        assertEquals(30, result.faces().size());
        assertEquals(9, result.count());
        assertFalse(result.faces().contains(new Face(0, 0, 0, Side.EAST)));
    }

    @Test
    void startOnlyCountsOne() {
        PreviewFill.Result result = PreviewFill.of(Map.of(Offset.ZERO, CellCategory.START));
        assertEquals(1, result.count());
        assertEquals(Optional.of(Offset.ZERO), result.start());
        assertTrue(result.faces().isEmpty());
    }

    @Test
    void countIsPresentPlusStartAndSkippedNeighboursGetAFace() {
        Map<Offset, CellCategory> cells = new LinkedHashMap<>();
        cells.put(new Offset(0, 0, 0), CellCategory.START);
        cells.put(new Offset(1, 0, 0), CellCategory.PRESENT);
        cells.put(new Offset(2, 0, 0), CellCategory.AIR);
        cells.put(new Offset(3, 0, 0), CellCategory.EXCLUDED);
        cells.put(new Offset(4, 0, 0), CellCategory.UNLOADED);
        PreviewFill.Result result = PreviewFill.of(cells);
        assertEquals(2, result.count());
        // The present cell borders the start block (mined, no face) and air (face).
        assertEquals(5, result.faces().size());
        assertFalse(result.faces().contains(new Face(1, 0, 0, Side.WEST)));
        assertTrue(result.faces().contains(new Face(1, 0, 0, Side.EAST)));
    }
}
