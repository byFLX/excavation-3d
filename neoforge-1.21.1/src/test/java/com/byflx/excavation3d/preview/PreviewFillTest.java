package com.byflx.excavation3d.preview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.byflx.excavation3d.preview.PreviewFill.Face;
import com.byflx.excavation3d.preview.PreviewFill.Side;
import com.byflx.excavation3d.shape.Offset;
import java.util.LinkedHashMap;
import java.util.List;
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

    @Test
    void contourCellsAreOnlyTheMinedOnes() {
        Map<Offset, CellCategory> cells = new LinkedHashMap<>();
        cells.put(new Offset(0, 0, 0), CellCategory.START);
        cells.put(new Offset(1, 0, 0), CellCategory.PRESENT);
        cells.put(new Offset(2, 0, 0), CellCategory.AIR);
        cells.put(new Offset(3, 0, 0), CellCategory.EXCLUDED);
        cells.put(new Offset(4, 0, 0), CellCategory.UNLOADED);
        assertEquals(List.of(new Offset(0, 0, 0), new Offset(1, 0, 0)), PreviewFill.minedCells(cells));
    }

    @Test
    void isolatedSkippedCellGetsNoOutline() {
        // A custom 3 × 1 × 1 row whose middle is air and a lone excluded cell further out.
        Map<Offset, CellCategory> cells = new LinkedHashMap<>();
        cells.put(new Offset(0, 0, 0), CellCategory.START);
        cells.put(new Offset(1, 0, 0), CellCategory.AIR);
        cells.put(new Offset(2, 0, 0), CellCategory.PRESENT);
        cells.put(new Offset(5, 0, 0), CellCategory.EXCLUDED);
        List<ContourEdges.Edge> edges = ContourEdges.of(PreviewFill.minedCells(cells));
        // Two separate blocks: 12 edges each, none around the air or the excluded cell.
        assertEquals(24, edges.size());
        for (ContourEdges.Edge edge : edges) {
            boolean aroundSkipped = switch (edge.axis()) {
                case X -> edge.x() == 1 || edge.x() == 5;
                case Y, Z -> edge.x() == 5 || edge.x() == 6;
            };
            assertFalse(aroundSkipped, edge.toString());
        }
    }
}
