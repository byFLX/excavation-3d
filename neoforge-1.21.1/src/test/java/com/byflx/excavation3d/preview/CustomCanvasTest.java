package com.byflx.excavation3d.preview;

import com.byflx.excavation3d.editor.CustomMaskDraft;
import com.byflx.excavation3d.shape.Cell;
import com.byflx.excavation3d.shape.ShapeTransform;
import java.util.BitSet;
import com.byflx.excavation3d.shape.ShapeMask;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CustomCanvasTest {
    @Test void growthImmediatelyExposesEmptyCellsWithoutAddingMiningSelection() {
        var draft = CustomMaskDraft.empty(3, 3, 1);
        draft.set(1, 1, 0, true);
        assertEquals(8, CustomCanvas.ghosts(draft.toMask(), 0).size());
        assertTrue(draft.resize(8, 3, 1));
        assertEquals(23, CustomCanvas.ghosts(draft.toMask(), 0).size());
        assertTrue(CustomCanvas.ghosts(draft.toMask(), 0).contains(new Cell(7, 2, 0)));
        assertTrue(draft.resize(8, 16, 8));
        assertEquals(128, CustomCanvas.ghosts(draft.toMask(), 7).size());
        assertEquals(1, draft.count());
    }

    @Test void evenAnInvalidEmptyDraftHasAVisibleCanvas() {
        var draft = CustomMaskDraft.empty(8, 16, 8);
        assertEquals(128, CustomCanvas.ghosts(draft.toMask(), 0).size());
        assertTrue(CustomCanvas.ghosts(draft.toMask(), 8).isEmpty());
    }

    @Test void picksMapBackToTheDraftForAllRotationsAndMirrorsOnAnUnequalGrid() {
        for (var transform : ShapeTransform.all()) {
            for (int x = 0; x < 5; x++) for (int y = 0; y < 3; y++) {
                Cell original = new Cell(x, y, 1);
                BitSet bits = new BitSet(); bits.set((1 * 3 + y) * 5 + x);
                ShapeMask mask = ShapeMask.of(5, 3, 2, bits, original);
                Cell displayed = transform.apply(mask).anchor();
                assertEquals(original, CustomCanvas.toDraft(displayed, 5, 3, transform));
            }
        }
    }
}
