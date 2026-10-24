package com.byflx.excavation3d.editor;

import static org.junit.jupiter.api.Assertions.*;

import com.byflx.excavation3d.shape.*;
import org.junit.jupiter.api.Test;

class EditorHistoryTest {
    private static final ShapeLimits LIMITS = ShapeLimits.DEFAULT;

    private static EditorDraft openedCustom() {
        return EditorDraft.fromConfig(ShapeConfig.of(new ShapeParams.Custom(
                ShapeMask.full(3, 2, 1, new Cell(1, 0, 0)))));
    }

    private static ShapeConfig config(EditorDraft draft) { return draft.evaluate(LIMITS).config(); }

    @Test void rotationOnAnOpenedCustomShapeIsTheFirstUndoStep() {
        EditorDraft draft = openedCustom();
        ShapeConfig before = config(draft);
        assertFalse(draft.canUndo());
        draft.rotateClockwise();
        ShapeConfig rotated = config(draft);
        assertNotEquals(before, rotated);
        assertTrue(draft.undo());
        assertEquals(before, config(draft));
        assertFalse(draft.canUndo());
        assertTrue(draft.redo());
        assertEquals(rotated, config(draft));
    }

    @Test void paintThenRotateUndoesRotationBeforePaintAndRedoesInOrder() {
        EditorDraft draft = openedCustom();
        ShapeConfig before = config(draft);
        assertTrue(draft.custom().set(0, 1, 0, false));
        ShapeConfig painted = config(draft);
        draft.rotateClockwise();
        ShapeConfig rotated = config(draft);
        assertTrue(draft.undo());
        assertEquals(painted, config(draft));
        assertTrue(draft.undo());
        assertEquals(before, config(draft));
        assertTrue(draft.redo());
        assertEquals(painted, config(draft));
        assertTrue(draft.redo());
        assertEquals(rotated, config(draft));
    }

    @Test void mirrorAfterUndoPaintClearsTheStaleRedo() {
        EditorDraft draft = openedCustom();
        ShapeConfig before = config(draft);
        draft.custom().set(0, 1, 0, false);
        assertTrue(draft.undo());
        assertTrue(draft.canRedo());
        draft.toggleMirror();
        assertFalse(draft.canRedo());
        assertFalse(draft.redo());
        assertTrue(draft.undo());
        assertEquals(before, config(draft));
    }

    @Test void customTransformStaysWithCustomAndTemplatesHaveNoHiddenTransform() {
        EditorDraft draft = openedCustom();
        draft.rotateClockwise();
        draft.toggleMirror();
        ShapeConfig custom = config(draft);
        for (ShapeType type : ShapeType.values()) {
            if (type == ShapeType.CUSTOM) continue;
            draft.setType(type);
            assertEquals(ShapeTransform.IDENTITY, draft.transform());
            assertEquals(ShapeConfig.of(ShapeTemplates.defaults(type)), config(draft));
            draft.rotateClockwise();
            draft.toggleMirror();
            assertEquals(ShapeTransform.IDENTITY, draft.transform());
        }
        draft.setType(ShapeType.CUSTOM);
        assertEquals(custom, config(draft));
    }

    @Test void everyResetIsOneStepAndKeepsAllOtherTypes() {
        for (ShapeType type : ShapeType.values()) {
            EditorDraft draft = openedCustom();
            draft.rotateClockwise();
            draft.custom().setAnchor(0, 0, 0);
            ShapeConfig otherCustom = config(draft);
            draft.setType(ShapeType.CUBOID);
            draft.setText(EditorDraft.WIDTH, "7");
            draft.setType(type);
            draft.setText(EditorDraft.WIDTH, "5");
            if (type == ShapeType.STAIR) draft.setStairAscending(false);
            if (type == ShapeType.CORRIDOR) draft.setCorridorUpward(false);
            ShapeConfig before = config(draft);
            String beforeWidth = draft.text(EditorDraft.WIDTH);
            draft.resetDefaults();
            assertEquals(ShapeConfig.of(ShapeTemplates.defaults(type)), config(draft));
            assertTrue(draft.undo());
            assertEquals(before, config(draft));
            assertEquals(beforeWidth, draft.text(EditorDraft.WIDTH));
            assertTrue(draft.redo());
            assertEquals(ShapeConfig.of(ShapeTemplates.defaults(type)), config(draft));
            if (type != ShapeType.CUBOID) {
                draft.setType(ShapeType.CUBOID);
                assertEquals("7", draft.text(EditorDraft.WIDTH));
            }
            if (type != ShapeType.CUSTOM) {
                draft.setType(ShapeType.CUSTOM);
                assertEquals(otherCustom, config(draft));
            }
        }
    }

    @Test void templateSizesDirectionAndResetShareChronologicalHistory() {
        EditorDraft draft = EditorDraft.fromConfig(ShapeConfig.of(ShapeTemplates.defaults(ShapeType.STAIR)));
        ShapeConfig defaults = config(draft);
        draft.setText(EditorDraft.WIDTH, "4");
        ShapeConfig sized = config(draft);
        draft.setStairAscending(false);
        ShapeConfig downward = config(draft);
        draft.resetDefaults();
        assertTrue(draft.undo());
        assertEquals(downward, config(draft));
        assertTrue(draft.undo());
        assertEquals(sized, config(draft));
        assertTrue(draft.undo());
        assertEquals(defaults, config(draft));
        assertFalse(draft.canUndo());
        draft.setText(EditorDraft.WIDTH, "6");
        assertFalse(draft.canRedo());
    }

    @Test void acceptedSizeAndResizeAreOneStepAndPaintStrokesStayGrouped() {
        EditorDraft draft = openedCustom();
        ShapeConfig before = config(draft);
        draft.edit(() -> {
            draft.setText(EditorDraft.WIDTH, "4");
            assertTrue(draft.custom().resize(4, 2, 1));
        });
        ShapeConfig resized = config(draft);
        draft.custom().beginStroke();
        draft.custom().set(3, 0, 0, true);
        draft.custom().set(3, 1, 0, true);
        draft.custom().endStroke();
        draft.toggleMirror();
        assertTrue(draft.undo());
        assertEquals(8, draft.custom().count());
        assertTrue(draft.undo());
        assertEquals(resized, config(draft));
        assertTrue(draft.undo());
        assertEquals(before, config(draft));
        assertFalse(draft.canUndo());
    }
    @Test void customResetAfterUndoClearsRedoAndRestoresMaskStartAndTransformInOneStep() {
        EditorDraft draft = openedCustom();
        draft.rotateClockwise();
        draft.toggleMirror();
        draft.custom().setAnchor(0, 1, 0);
        ShapeConfig before = config(draft);
        draft.custom().set(0, 0, 0, false);
        assertTrue(draft.undo());
        assertTrue(draft.canRedo());
        draft.resetDefaults();
        assertFalse(draft.canRedo());
        assertEquals(ShapeConfig.of(ShapeTemplates.defaults(ShapeType.CUSTOM)), config(draft));
        assertTrue(draft.undo());
        assertEquals(before, config(draft));
        assertTrue(draft.redo());
        assertEquals(ShapeConfig.of(ShapeTemplates.defaults(ShapeType.CUSTOM)), config(draft));
    }
}
