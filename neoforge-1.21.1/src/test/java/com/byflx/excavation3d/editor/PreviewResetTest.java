package com.byflx.excavation3d.editor;

import com.byflx.excavation3d.preview.PreviewGesture;
import com.byflx.excavation3d.preview.PreviewPicking;
import com.byflx.excavation3d.shape.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PreviewResetTest {
    @Test void doubleClickRollsBackInstantTemplateConversionWithoutRedoOrDirtyState() {
        for (ShapeType type : ShapeType.values()) {
            if (type == ShapeType.CUSTOM) continue;
            var draft = EditorDraft.fromConfig(ShapeConfig.of(ShapeTemplates.defaults(type)));
            draft.rotateClockwise();
            draft.toggleMirror();
            var before = draft.evaluate(ShapeLimits.DEFAULT).config();
            var save = new DraftSaveState();
            var gesture = new PreviewGesture();
            gesture.press(0, 50, 60, true);
            assertEquals(PreviewGesture.Release.CLICK, gesture.release(0, 50, 60, true, 1000));
            Runnable restore = draft.previewRollback();
            long generation = save.submit();
            var mask = draft.evaluate(ShapeLimits.DEFAULT).shape().orElseThrow().mask();
            var edge = mask.positions().stream().filter(c -> c.y() == mask.height() - 1).findFirst().orElseThrow();
            assertEquals(EditorDraft.PreviewEdit.CHANGED, draft.editTemplatePreview(
                    new PreviewPicking.Hit(edge, PreviewPicking.Face.UP, true, 1), true, ShapeLimits.DEFAULT));
            save.edited();
            assertEquals(ShapeType.CUSTOM, draft.type(), "the single click edits immediately");
            assertTrue(save.isDirty());
            gesture.press(0, 50, 60, true);
            assertEquals(PreviewGesture.Release.RESET, gesture.release(0, 50, 60, true, 1200));
            assertTrue(save.rollbackEdit(generation, save.submit()));
            restore.run();
            assertEquals(before, draft.evaluate(ShapeLimits.DEFAULT).config());
            assertFalse(draft.canUndo());
            assertFalse(draft.canRedo());
            assertFalse(draft.redo());
            assertFalse(save.isDirty());
        }
    }

    @Test void rollbackRestoresCustomRedoAndFullUndoHistoryInsteadOfAddingAnUndoStep() {
        var draft = EditorDraft.defaults();
        draft.setType(ShapeType.CUSTOM);
        var custom = draft.custom();
        for (int i = 0; i < CustomMaskDraft.MAX_UNDO + 2; i++) custom.set(0, 0, 0, i % 2 == 0);
        assertTrue(draft.undo());
        var before = custom.toMask();
        Runnable restore = draft.previewRollback();
        custom.set(2, 2, 0, true);
        assertFalse(draft.canRedo());
        restore.run();
        assertSame(custom, draft.custom());
        assertEquals(before, custom.toMask());
        assertTrue(draft.redo(), "pre-existing redo survives the reset");
        assertFalse(custom.get(2, 2, 0), "reset click never appears in redo");
        int undone = 0;
        while (draft.undo()) undone++;
        assertEquals(CustomMaskDraft.MAX_UNDO, undone);
    }

    @Test void dirtyStateBeforeTheClickSurvivesButInterveningEditsCannotBeRetracted() {
        var save = new DraftSaveState();
        save.edited();
        long before = save.submit();
        save.edited();
        assertTrue(save.rollbackEdit(before, save.submit()));
        assertTrue(save.isDirty());
        save.edited();
        long click = save.submit();
        save.edited();
        assertFalse(save.rollbackEdit(before, click));
        save.accepted(save.submit());
        assertFalse(save.rollbackEdit(before, save.submit()));
    }

    @Test void rollbackAtTheUndoLimitPreservesTheOldestEntryAndConversionBoundary() {
        var draft = EditorDraft.defaults();
        var config = draft.evaluate(ShapeLimits.DEFAULT).config();
        assertEquals(EditorDraft.PreviewEdit.CHANGED, draft.editTemplatePreview(
                new PreviewPicking.Hit(new Cell(0, 2, 0), PreviewPicking.Face.UP, true, 1),
                true, ShapeLimits.DEFAULT));
        for (int i = 0; i < CustomMaskDraft.MAX_UNDO; i++) draft.custom().set(1, 3, 0, i % 2 == 0);
        Runnable restore = draft.previewRollback();
        draft.custom().set(2, 3, 0, true); // Temporarily displaces the oldest undo entry.
        restore.run();
        for (int i = 0; i < CustomMaskDraft.MAX_UNDO; i++) assertTrue(draft.undo());
        assertTrue(draft.undo(), "the original conversion boundary remains below all custom edits");
        assertEquals(config, draft.evaluate(ShapeLimits.DEFAULT).config());
        assertTrue(draft.redo());
        assertFalse(draft.custom().get(2, 3, 0));
    }

    @Test void rollbackPreservesAnExistingTemplateConversionRedo() {
        var draft = EditorDraft.defaults();
        var config = draft.evaluate(ShapeLimits.DEFAULT).config();
        var hit = new PreviewPicking.Hit(new Cell(0, 2, 0), PreviewPicking.Face.UP, true, 1);
        assertEquals(EditorDraft.PreviewEdit.CHANGED, draft.editTemplatePreview(hit, true, ShapeLimits.DEFAULT));
        var firstConversion = draft.custom().toMask();
        assertTrue(draft.undo());
        Runnable restore = draft.previewRollback();
        assertEquals(EditorDraft.PreviewEdit.CHANGED, draft.editTemplatePreview(
                new PreviewPicking.Hit(new Cell(2, 2, 0), PreviewPicking.Face.UP, true, 1), true, ShapeLimits.DEFAULT));
        restore.run();
        assertEquals(config, draft.evaluate(ShapeLimits.DEFAULT).config());
        assertTrue(draft.redo());
        assertEquals(firstConversion, draft.custom().toMask());
    }
}
