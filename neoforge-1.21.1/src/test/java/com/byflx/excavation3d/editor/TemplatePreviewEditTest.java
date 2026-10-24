package com.byflx.excavation3d.editor;

import static org.junit.jupiter.api.Assertions.*;

import com.byflx.excavation3d.preview.PreviewGesture;
import com.byflx.excavation3d.preview.PreviewPicking;
import com.byflx.excavation3d.shape.*;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class TemplatePreviewEditTest {
    private static final ShapeLimits LIMITS = ShapeLimits.DEFAULT;
    private static final List<ShapeParams> TEMPLATES = List.of(
            new ShapeParams.Cuboid(3, 2, 2), new ShapeParams.Plus(5, 3, 2, 1),
            new ShapeParams.Stair(2, 3, 1, 1, true), new ShapeParams.Stair(2, 3, 1, 1, false),
            new ShapeParams.Corridor(2, 2, 3, 1, 1, true),
            new ShapeParams.Corridor(2, 2, 3, 1, 1, false));

    private static ShapeMask shown(EditorDraft draft) {
        return draft.transform().apply(draft.custom().toMask());
    }

    private static Set<Cell> relative(ShapeMask mask) {
        Cell a = mask.anchor();
        return mask.positions().stream().map(c -> new Cell(c.x() - a.x(), c.y() - a.y(), c.z() - a.z()))
                .collect(Collectors.toSet());
    }

    private static PreviewPicking.Hit hit(Cell cell, PreviewPicking.Face face, boolean selected) {
        return new PreviewPicking.Hit(cell, face, selected, 1);
    }

    @Test void firstAddPreservesEveryTemplateAndTransformAndOneUndoRestoresExactConfig() {
        for (ShapeParams params : TEMPLATES) {
            for (ShapeTransform transform : ShapeTransform.all()) {
                ShapeConfig config = ShapeConfig.of(params, transform);
                EditorDraft draft = EditorDraft.fromConfig(config);
                // Keep raw input too, rather than regenerating the fields from the config on undo.
                draft.setText(EditorDraft.WIDTH, "02");
                if (params instanceof ShapeParams.Plus) draft.setText(EditorDraft.WIDTH, "05");
                ShapeConfig before = draft.evaluate(LIMITS).config();
                ShapeMask template = draft.evaluate(LIMITS).shape().orElseThrow().mask();
                Cell edge = template.positions().stream()
                        .filter(c -> c.y() == template.height() - 1).findFirst().orElseThrow();
                Cell target = PreviewPicking.Face.UP.neighbour(edge);
                Set<Cell> expected = new HashSet<>(relative(template));
                expected.add(new Cell(target.x() - template.anchor().x(), target.y() - template.anchor().y(),
                        target.z() - template.anchor().z()));
                assertEquals(EditorDraft.PreviewEdit.CHANGED,
                        draft.editTemplatePreview(hit(edge, PreviewPicking.Face.UP, true), true, LIMITS));
                assertEquals(ShapeType.CUSTOM, draft.type());
                assertEquals(transform, draft.transform());
                assertEquals(expected, relative(shown(draft)));
                assertTrue(shown(draft).contains(shown(draft).anchor()));
                assertTrue(draft.evaluate(LIMITS).isValid());
                ShapeMask edited = shown(draft);
                assertTrue(draft.canUndo());
                assertTrue(draft.undo());
                assertEquals(before, draft.evaluate(LIMITS).config());
                assertEquals(params instanceof ShapeParams.Plus ? "05" : "02", draft.text(EditorDraft.WIDTH));
                assertFalse(draft.canUndo());
                assertTrue(draft.canRedo());
                assertTrue(draft.redo());
                assertEquals(ShapeType.CUSTOM, draft.type());
                assertEquals(edited, shown(draft));
            }
        }
    }

    @Test void firstRemovalIsOneUndoStepAndNeverRemovesTheStart() {
        for (ShapeParams params : TEMPLATES) {
            for (ShapeTransform transform : ShapeTransform.all()) {
                ShapeConfig config = ShapeConfig.of(params, transform);
                EditorDraft draft = EditorDraft.fromConfig(config);
                ShapeMask template = draft.evaluate(LIMITS).shape().orElseThrow().mask();
                assertEquals(EditorDraft.PreviewEdit.UNCHANGED, draft.editTemplatePreview(
                        hit(template.anchor(), PreviewPicking.Face.FRONT, true), false, LIMITS));
                assertEquals(config, draft.evaluate(LIMITS).config());
                Cell remove = template.positions().stream().filter(c -> !c.equals(template.anchor()))
                        .findFirst().orElseThrow();
                assertEquals(EditorDraft.PreviewEdit.CHANGED,
                        draft.editTemplatePreview(hit(remove, PreviewPicking.Face.FRONT, true), false, LIMITS));
                Set<Cell> expected = new HashSet<>(template.positions());
                expected.remove(remove);
                assertEquals(expected, new HashSet<>(shown(draft).positions()));
                assertEquals(template.anchor(), shown(draft).anchor());
                assertTrue(draft.undo());
                assertEquals(config, draft.evaluate(LIMITS).config());
                assertTrue(draft.redo());
                assertEquals(expected, new HashSet<>(shown(draft).positions()));
            }
        }
    }

    @Test void neighboursGrowExactlyOneCellOnEachOfTheSixSidesAndKeepAnchorRelativeSelection() {
        for (PreviewPicking.Face face : PreviewPicking.Face.values()) {
            EditorDraft draft = EditorDraft.fromConfig(ShapeConfig.of(new ShapeParams.Cuboid(1, 1, 1)));
            Cell origin = new Cell(0, 0, 0);
            assertEquals(EditorDraft.PreviewEdit.CHANGED,
                    draft.editTemplatePreview(hit(origin, face, true), true, LIMITS));
            ShapeMask result = shown(draft);
            assertEquals(Set.of(origin, face.neighbour(origin)), relative(result));
            assertEquals(face == PreviewPicking.Face.WEST || face == PreviewPicking.Face.EAST ? 2 : 1, result.width());
            assertEquals(face == PreviewPicking.Face.DOWN || face == PreviewPicking.Face.UP ? 2 : 1, result.height());
            assertEquals(face == PreviewPicking.Face.FRONT || face == PreviewPicking.Face.BACK ? 2 : 1, result.depth());
            assertTrue(result.contains(result.anchor()));
        }
    }

    @Test void ghostAndFloorClicksAddTheClickedCellAndRightClickOnEitherDoesNothing() {
        EditorDraft draft = EditorDraft.fromConfig(ShapeConfig.of(new ShapeParams.Plus(3, 3, 1, 1)));
        var ghost = hit(new Cell(0, 0, 0), PreviewPicking.Face.UP, false);
        assertEquals(EditorDraft.PreviewEdit.UNCHANGED, draft.editTemplatePreview(ghost, false, LIMITS));
        assertEquals(EditorDraft.PreviewEdit.CHANGED, draft.editTemplatePreview(ghost, true, LIMITS));
        assertTrue(draft.custom().get(0, 0, 0));
        assertEquals(6, draft.custom().count());
        assertTrue(draft.undo());
        var floor = hit(new Cell(-1, 0, 0), PreviewPicking.Face.UP, false);
        assertEquals(EditorDraft.PreviewEdit.CHANGED, draft.editTemplatePreview(floor, true, LIMITS));
        assertEquals(4, draft.custom().width());
        assertTrue(draft.custom().get(0, 0, 0));
    }

    @Test void axisAndSelectionLimitRejectionsLeaveTemplatePreviousCustomAndHistoryUntouched() {
        for (PreviewPicking.Face face : PreviewPicking.Face.values()) {
            ShapeConfig config = ShapeConfig.of(new ShapeParams.Cuboid(2, 2, 2));
            EditorDraft draft = EditorDraft.fromConfig(config);
            CustomMaskDraft previous = draft.custom();
            Cell cell = switch (face) {
                case WEST, DOWN, FRONT -> new Cell(0, 0, 0);
                default -> new Cell(1, 1, 1);
            };
            assertEquals(EditorDraft.PreviewEdit.LIMIT,
                    draft.editTemplatePreview(hit(cell, face, true), true, new ShapeLimits(2, 100)));
            assertEquals(config, draft.evaluate(LIMITS).config());
            assertSame(previous, draft.custom());
            assertFalse(draft.canUndo());
            assertFalse(draft.canRedo());
            assertEquals(EditorDraft.PreviewEdit.LIMIT,
                    draft.editTemplatePreview(hit(cell, face, true), true, new ShapeLimits(4, 8)));
        }
    }

    @Test void invalidFieldsAndInvalidGeneratedTemplatesCannotConvertOrEdit() {
        EditorDraft draft = EditorDraft.defaults();
        CustomMaskDraft previous = draft.custom();
        draft.setText(EditorDraft.WIDTH, "");
        var click = hit(new Cell(0, 0, 0), PreviewPicking.Face.UP, true);
        assertEquals(EditorDraft.PreviewEdit.INVALID, draft.editTemplatePreview(click, true, LIMITS));
        assertEquals(ShapeType.CUBOID, draft.type());
        assertEquals("", draft.text(EditorDraft.WIDTH));
        draft.setType(ShapeType.STAIR);
        draft.setText(EditorDraft.STEPS, "32");
        draft.setText(EditorDraft.STEP_HEIGHT, "2");
        assertEquals(EditorDraft.PreviewEdit.INVALID, draft.editTemplatePreview(click, false, LIMITS));
        assertEquals(ShapeType.STAIR, draft.type());
        assertSame(previous, draft.custom());
        assertFalse(draft.canRedo());
    }

    @Test void growthCanReachTheExactServerAxisLimitAndInteriorGhostsNeedNoGrowth() {
        EditorDraft draft = EditorDraft.fromConfig(ShapeConfig.of(new ShapeParams.Cuboid(1, 1, 1)));
        assertEquals(EditorDraft.PreviewEdit.CHANGED, draft.editTemplatePreview(
                hit(new Cell(0, 0, 0), PreviewPicking.Face.WEST, true), true, new ShapeLimits(2, 10)));
        assertEquals(2, draft.custom().width());
        assertTrue(draft.evaluate(new ShapeLimits(2, 10)).isValid());
        draft = EditorDraft.fromConfig(ShapeConfig.of(new ShapeParams.Plus(3, 3, 1, 1)));
        assertEquals(EditorDraft.PreviewEdit.CHANGED, draft.editTemplatePreview(
                hit(new Cell(0, 0, 0), PreviewPicking.Face.UP, false), true, new ShapeLimits(3, 10)));
        assertEquals(3, draft.custom().width());
        assertEquals(3, draft.custom().height());
        assertTrue(draft.evaluate(new ShapeLimits(3, 10)).isValid());
    }

    @Test void conversionBoundaryPreservesAnExistingCustomDraftAndItsHistory() {
        EditorDraft draft = EditorDraft.defaults();
        draft.setType(ShapeType.CUSTOM);
        assertTrue(draft.custom().set(1, 1, 0, true));
        assertTrue(draft.custom().set(0, 0, 0, true));
        CustomMaskDraft previous = draft.custom();
        ShapeMask before = previous.toMask();
        draft.setType(ShapeType.PLUS);
        ShapeConfig template = draft.evaluate(LIMITS).config();
        assertEquals(EditorDraft.PreviewEdit.CHANGED, draft.editTemplatePreview(
                hit(new Cell(0, 0, 0), PreviewPicking.Face.UP, false), true, LIMITS));
        assertTrue(draft.undo());
        assertEquals(template, draft.evaluate(LIMITS).config());
        assertSame(previous, draft.custom());
        assertEquals(before, draft.custom().toMask());
        assertTrue(draft.custom().canUndo());
        assertTrue(draft.redo());
        assertEquals(6, draft.custom().count());
    }

    @Test void laterCustomEditsUndoBeforeConversionAndRedoInTheSameOrder() {
        EditorDraft draft = EditorDraft.defaults();
        CustomMaskDraft previous = draft.custom();
        ShapeConfig template = draft.evaluate(LIMITS).config();
        assertEquals(EditorDraft.PreviewEdit.CHANGED, draft.editTemplatePreview(
                hit(new Cell(0, 2, 0), PreviewPicking.Face.UP, true), true, LIMITS));
        ShapeMask first = shown(draft);
        assertTrue(draft.custom().set(1, 3, 0, true));
        ShapeMask second = shown(draft);
        assertTrue(draft.undo());
        assertEquals(first, shown(draft));
        assertEquals(ShapeType.CUSTOM, draft.type());
        assertTrue(draft.undo());
        assertEquals(template, draft.evaluate(LIMITS).config());
        assertSame(previous, draft.custom());
        assertTrue(draft.redo());
        assertEquals(first, shown(draft));
        assertTrue(draft.redo());
        assertEquals(second, shown(draft));
        assertTrue(draft.undo());
        assertTrue(draft.undo());
        draft.setText(EditorDraft.WIDTH, "4");
        assertFalse(draft.canRedo());
        assertFalse(draft.redo());
    }

    @Test void clicksEditButExactlyFourGuiPixelsRemainADragForEitherButton() {
        for (int button : new int[] {0, 1}) {
            for (double movement : new double[] {3.99, 4, 5}) {
                EditorDraft draft = EditorDraft.defaults();
                ShapeConfig template = draft.evaluate(LIMITS).config();
                PreviewGesture gesture = new PreviewGesture();
                gesture.press(button, 10, 10, true);
                var motion = gesture.drag(button, 10 + movement, 10, movement, 0, true);
                var release = gesture.release(button, 10 + movement, 10, true, 1000);
                if (release == PreviewGesture.Release.CLICK) {
                    assertEquals(EditorDraft.PreviewEdit.CHANGED, draft.editTemplatePreview(
                            hit(new Cell(0, 2, 0), PreviewPicking.Face.UP, true), button == 0, LIMITS));
                }
                assertEquals(movement < 4 ? PreviewGesture.Release.CLICK : PreviewGesture.Release.DRAG, release);
                if (movement >= 4) {
                    assertEquals(movement, motion.dx());
                    assertEquals(template, draft.evaluate(LIMITS).config());
                } else assertEquals(ShapeType.CUSTOM, draft.type());
            }
        }
    }
}
