package com.byflx.excavation3d.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.byflx.excavation3d.shape.Cell;
import com.byflx.excavation3d.shape.ShapeMask;
import org.junit.jupiter.api.Test;

class CustomMaskDraftTest {
    @Test
    void strokeSetsWithoutTogglingAndIsOneUndoStep() {
        CustomMaskDraft draft = CustomMaskDraft.empty(4, 4, 1);
        draft.beginStroke();
        assertTrue(draft.set(0, 0, 0, true));
        assertTrue(draft.set(1, 0, 0, true));
        // Passing the same cell again in the same drag must not flip it back.
        assertFalse(draft.set(0, 0, 0, true));
        draft.endStroke();
        assertEquals(2, draft.count());
        assertTrue(draft.undo());
        assertEquals(0, draft.count());
        assertFalse(draft.canUndo());
        assertTrue(draft.redo());
        assertEquals(2, draft.count());
    }

    @Test
    void strokeWithoutChangeRecordsNoUndoStep() {
        CustomMaskDraft draft = CustomMaskDraft.empty(2, 2, 1);
        draft.beginStroke();
        assertFalse(draft.set(0, 0, 0, false));
        draft.endStroke();
        assertFalse(draft.canUndo());
    }

    @Test
    void deselectingRemovesCellsFromTheMask() {
        CustomMaskDraft draft = CustomMaskDraft.fromMask(ShapeMask.full(3, 3, 1, new Cell(1, 0, 0)));
        draft.beginStroke();
        draft.set(1, 1, 0, false);
        draft.endStroke();
        ShapeMask mask = draft.toMask();
        assertEquals(8, mask.count());
        assertFalse(mask.contains(1, 1, 0));
    }

    @Test
    void growingAddsEmptyCellsAndKeepsCoordinates() {
        CustomMaskDraft draft = CustomMaskDraft.empty(2, 2, 1);
        draft.set(1, 1, 0, true);
        CustomMaskDraft.ResizeCheck check = draft.checkResize(4, 3, 2);
        assertTrue(check.allowedWithoutConfirmation());
        assertTrue(draft.resize(4, 3, 2));
        assertEquals(1, draft.count());
        assertTrue(draft.get(1, 1, 0));
        assertEquals(4, draft.width());
        assertEquals(2, draft.depth());
    }

    @Test
    void shrinkingReportsLossAndUndoRestores() {
        CustomMaskDraft draft = CustomMaskDraft.fromMask(ShapeMask.full(4, 4, 2, new Cell(0, 0, 0)));
        CustomMaskDraft.ResizeCheck check = draft.checkResize(2, 4, 1);
        assertEquals(24, check.lostCells());
        assertFalse(check.allowedWithoutConfirmation());
        assertTrue(draft.resize(2, 4, 1));
        assertEquals(8, draft.count());
        assertTrue(draft.undo());
        assertEquals(32, draft.count());
        assertEquals(4, draft.width());
        assertEquals(2, draft.depth());
    }

    @Test
    void resizeThatWouldLoseTheAnchorIsRefused() {
        CustomMaskDraft draft = CustomMaskDraft.empty(5, 5, 1);
        assertEquals(new Cell(2, 2, 0), draft.anchor());
        assertFalse(draft.checkResize(2, 5, 1).anchorInside());
        assertFalse(draft.resize(2, 5, 1));
        assertEquals(5, draft.width());
        assertThrows(IllegalArgumentException.class, () -> draft.checkResize(33, 1, 1));
    }

    @Test
    void layerOperations() {
        CustomMaskDraft draft = CustomMaskDraft.empty(3, 2, 3);
        assertTrue(draft.fillLayer(0, true));
        assertFalse(draft.fillLayer(0, true));
        assertEquals(6, draft.count());
        draft.set(0, 0, 0, false);
        draft.copyLayer(0);
        assertTrue(draft.pasteLayer(2));
        assertEquals(10, draft.count());
        assertFalse(draft.get(0, 0, 2));
        assertTrue(draft.get(1, 0, 2));
        assertTrue(draft.fillLayer(0, false));
        assertEquals(6, draft.count()); // The selected start block survives clearing its layer.
        assertTrue(draft.undo());
        assertEquals(10, draft.count());
    }

    @Test
    void anchorCanBeMovedAndUndone() {
        CustomMaskDraft draft = CustomMaskDraft.empty(3, 3, 2);
        assertTrue(draft.setAnchor(0, 2, 1));
        assertEquals(new Cell(0, 2, 1), draft.toMask().anchor());
        assertFalse(draft.setAnchor(3, 0, 0));
        assertTrue(draft.undo());
        assertEquals(new Cell(1, 1, 0), draft.anchor());
    }

    @Test
    void startBlockCannotBePaintedClearedOrPastedAway() {
        var draft = CustomMaskDraft.fromMask(ShapeMask.full(3, 3, 2, new Cell(1, 1, 0)));
        assertFalse(draft.set(1, 1, 0, false));
        assertFalse(draft.canUndo());
        assertTrue(draft.fillLayer(0, false));
        assertTrue(draft.get(1, 1, 0));
        assertTrue(draft.undo());
        assertEquals(18, draft.count());
        draft.fillLayer(1, false);
        draft.copyLayer(1);
        assertTrue(draft.pasteLayer(0));
        assertEquals(1, draft.count());
        assertTrue(draft.get(1, 1, 0));
        assertTrue(draft.undo());
        assertEquals(9, draft.count());
    }

    @Test
    void failedShrinkCannotPreventLaterGrowthOnAnyAxis() {
        var draft = CustomMaskDraft.empty(3, 3, 3);
        draft.setAnchor(2, 2, 2);
        assertFalse(draft.resize(2, 3, 3));
        assertTrue(draft.checkResize(8, 3, 3).allowedWithoutConfirmation());
        assertTrue(draft.resize(8, 3, 3));
        assertTrue(draft.resize(8, 16, 3));
        assertTrue(draft.resize(8, 16, 8));
        assertEquals(new Cell(2, 2, 2), draft.anchor());
    }

    @Test
    void replacingTheGridIsOneUndoStep() {
        CustomMaskDraft draft = CustomMaskDraft.empty(2, 2, 1);
        draft.set(0, 0, 0, true);
        ShapeMask template = ShapeMask.full(4, 3, 2, new Cell(1, 1, 0));
        draft.replaceWith(template);
        assertEquals(template, draft.toMask());
        assertTrue(draft.undo());
        assertEquals(2, draft.width());
        assertEquals(1, draft.count());
        assertTrue(draft.get(0, 0, 0));
        assertTrue(draft.redo());
        assertEquals(template, draft.toMask());
    }

    @Test
    void toMaskIsAnIndependentCopy() {
        CustomMaskDraft draft = CustomMaskDraft.empty(2, 1, 1);
        draft.set(0, 0, 0, true);
        ShapeMask mask = draft.toMask();
        draft.set(1, 0, 0, true);
        assertEquals(1, mask.count());
        assertEquals(2, draft.toMask().count());
    }

    @Test
    void undoHistoryIsBounded() {
        CustomMaskDraft draft = CustomMaskDraft.empty(32, 32, 1);
        for (int i = 0; i < CustomMaskDraft.MAX_UNDO + 10; i++) {
            draft.set(i % 32, i / 32, 0, true);
        }
        int undone = 0;
        while (draft.undo()) {
            undone++;
        }
        assertEquals(CustomMaskDraft.MAX_UNDO, undone);
    }
}
