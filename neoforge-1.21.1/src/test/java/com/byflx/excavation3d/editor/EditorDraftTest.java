package com.byflx.excavation3d.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.byflx.excavation3d.shape.Cell;
import com.byflx.excavation3d.shape.GeneratedShape;
import com.byflx.excavation3d.shape.NumericInput;
import com.byflx.excavation3d.shape.ShapeConfig;
import com.byflx.excavation3d.shape.ShapeGenerator;
import com.byflx.excavation3d.shape.ShapeIssue;
import com.byflx.excavation3d.shape.ShapeLimits;
import com.byflx.excavation3d.shape.ShapeMask;
import com.byflx.excavation3d.shape.ShapeParams;
import com.byflx.excavation3d.shape.ShapeTransform;
import com.byflx.excavation3d.shape.ShapeType;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class EditorDraftTest {
    private static GeneratedShape shape(EditorDraft draft) {
        return draft.evaluate(ShapeLimits.DEFAULT).shape().orElseThrow();
    }

    @Test
    void typedFieldsDriveTheSameGeneratorAsTheServer() {
        // Plan §12: 7 × 5 × 2 entered as text gives 70 positions.
        EditorDraft draft = EditorDraft.defaults();
        draft.setText(EditorDraft.WIDTH, "7");
        draft.setText(EditorDraft.HEIGHT, "5");
        draft.setText(EditorDraft.DEPTH, "2");
        EditorDraft.Evaluation evaluation = draft.evaluate(ShapeLimits.DEFAULT);
        assertTrue(evaluation.isValid());
        assertEquals(70, evaluation.shape().orElseThrow().count());
        assertEquals(ShapeGenerator.generate(evaluation.config(), ShapeLimits.DEFAULT), evaluation.result());
    }

    @Test
    void fieldsMayBeEmptyWhileTypingAndAreNeverCorrected() {
        EditorDraft draft = EditorDraft.defaults();
        draft.setText(EditorDraft.WIDTH, "");
        EditorDraft.Evaluation evaluation = draft.evaluate(ShapeLimits.DEFAULT);
        assertFalse(evaluation.isValid());
        assertEquals(Map.of(EditorDraft.WIDTH, NumericInput.Problem.EMPTY), evaluation.fieldErrors());
        assertNull(evaluation.config());
        assertEquals("", draft.text(EditorDraft.WIDTH));

        for (String bad : List.of("0", "-3", "1.5", "1,5", "abc", "9999999", "33")) {
            draft.setText(EditorDraft.WIDTH, bad);
            assertFalse(draft.evaluate(ShapeLimits.DEFAULT).isValid(), bad);
            assertEquals(bad, draft.text(EditorDraft.WIDTH));
        }
    }

    @Test
    void derivedOversizeIsAnIssueNotAFieldError() {
        EditorDraft draft = EditorDraft.defaults();
        draft.setType(ShapeType.STAIR);
        draft.setText(EditorDraft.STEPS, "32");
        draft.setText(EditorDraft.STEP_HEIGHT, "2");
        EditorDraft.Evaluation evaluation = draft.evaluate(ShapeLimits.DEFAULT);
        assertTrue(evaluation.fieldErrors().isEmpty());
        assertFalse(evaluation.isValid());
        ShapeGenerator.Invalid invalid = (ShapeGenerator.Invalid) evaluation.result();
        assertEquals(ShapeIssue.Code.AXIS_TOO_LARGE, invalid.issues().get(0).code());
    }

    @Test
    void serverLimitsApplyToTheDraft() {
        EditorDraft draft = EditorDraft.defaults();
        draft.setText(EditorDraft.WIDTH, "20");
        assertTrue(draft.evaluate(ShapeLimits.DEFAULT).isValid());
        EditorDraft.Evaluation lowered = draft.evaluate(new ShapeLimits(16, 512));
        assertEquals(Map.of(EditorDraft.WIDTH, NumericInput.Problem.ABOVE_MAX), lowered.fieldErrors());
    }

    @Test
    void switchingTypesKeepsEachTypesValues() {
        EditorDraft draft = EditorDraft.defaults();
        draft.setText(EditorDraft.WIDTH, "9");
        draft.setType(ShapeType.PLUS);
        assertEquals("3", draft.text(EditorDraft.WIDTH));
        draft.setType(ShapeType.CUBOID);
        assertEquals("9", draft.text(EditorDraft.WIDTH));
    }

    @Test
    void plusMinusStepsOnlyValidValuesWithinLimits() {
        EditorDraft draft = EditorDraft.defaults();
        assertEquals(Optional.of("4"), draft.stepped(EditorDraft.WIDTH, 1, ShapeLimits.DEFAULT));
        draft.setText(EditorDraft.WIDTH, "1");
        assertEquals(Optional.empty(), draft.stepped(EditorDraft.WIDTH, -1, ShapeLimits.DEFAULT));
        draft.setText(EditorDraft.WIDTH, "32");
        assertEquals(Optional.empty(), draft.stepped(EditorDraft.WIDTH, 1, ShapeLimits.DEFAULT));
        draft.setText(EditorDraft.WIDTH, "");
        assertEquals(Optional.empty(), draft.stepped(EditorDraft.WIDTH, 1, ShapeLimits.DEFAULT));
    }

    @Test
    void consecutiveStepperPressesAccumulateInBothDirections() {
        EditorDraft draft = EditorDraft.defaults();
        for (ShapeType type : List.of(ShapeType.CUBOID, ShapeType.CUSTOM)) {
            draft.setType(type);
            draft.setText(EditorDraft.WIDTH, "3");
            for (int expected : new int[] {4, 5}) {
                draft.setText(EditorDraft.WIDTH, draft.stepped(EditorDraft.WIDTH, 1, ShapeLimits.DEFAULT).orElseThrow());
                assertEquals(Integer.toString(expected), draft.text(EditorDraft.WIDTH));
            }
            for (int expected : new int[] {4, 3}) {
                draft.setText(EditorDraft.WIDTH, draft.stepped(EditorDraft.WIDTH, -1, ShapeLimits.DEFAULT).orElseThrow());
                assertEquals(Integer.toString(expected), draft.text(EditorDraft.WIDTH));
            }
        }
    }

    @Test
    void surfaceAndLineTemplatesAreCuboidValues() {
        EditorDraft draft = EditorDraft.defaults();
        draft.setText(EditorDraft.DEPTH, "4");
        draft.applySurfaceTemplate();
        assertEquals(9, shape(draft).count());
        draft.applyLineTemplate();
        assertEquals(3, shape(draft).count());
        assertEquals(1, shape(draft).height());
    }

    @Test
    void draftRestoresEveryStoredConfig() {
        List<ShapeConfig> configs = List.of(
                ShapeConfig.of(new ShapeParams.Cuboid(7, 5, 2), new ShapeTransform(1, false)),
                ShapeConfig.of(new ShapeParams.Plus(5, 5, 2, 1), new ShapeTransform(0, true)),
                ShapeConfig.of(new ShapeParams.Stair(3, 4, 2, 1, false)),
                ShapeConfig.of(new ShapeParams.Corridor(3, 3, 6, 1, 1, false), new ShapeTransform(2, true)),
                ShapeConfig.of(new ShapeParams.Custom(ShapeMask.full(2, 3, 2, new Cell(1, 2, 0)))));
        for (ShapeConfig config : configs) {
            EditorDraft draft = EditorDraft.fromConfig(config);
            if (config.type() == ShapeType.CUSTOM || config.transform().isIdentity()) {
                assertEquals(config, draft.evaluate(ShapeLimits.DEFAULT).config(), config.toString());
            } else {
                assertEquals(ShapeType.CUSTOM, draft.type());
                assertEquals(ShapeTransform.IDENTITY, draft.transform());
                assertEquals(config.orientationMode(), draft.evaluate(ShapeLimits.DEFAULT).config().orientationMode());
                assertEquals(((ShapeGenerator.Valid) ShapeGenerator.generate(config, ShapeLimits.DEFAULT)).shape().mask(),
                        draft.evaluate(ShapeLimits.DEFAULT).shape().orElseThrow().mask(), config.toString());
            }
        }
    }

    @Test
    void transformButtonsChangeMaskAndAnchorTogether() {
        EditorDraft draft = EditorDraft.defaults();
        draft.setText(EditorDraft.WIDTH, "3");
        draft.setText(EditorDraft.HEIGHT, "1");
        assertTrue(draft.convertToCustom(ShapeLimits.DEFAULT));
        GeneratedShape before = shape(draft);
        draft.rotateClockwise();
        GeneratedShape rotated = shape(draft);
        assertEquals(1, rotated.width());
        assertEquals(3, rotated.height());
        assertTrue(rotated.mask().contains(rotated.anchor()));
        draft.toggleMirror();
        draft.toggleMirror();
        draft.rotateClockwise();
        draft.rotateClockwise();
        draft.rotateClockwise();
        assertEquals(before, shape(draft));
    }

    @Test
    void convertingATemplateKeepsTheSelection() {
        EditorDraft draft = EditorDraft.defaults();
        draft.setType(ShapeType.PLUS);
        draft.setText(EditorDraft.WIDTH, "4");
        draft.setText(EditorDraft.HEIGHT, "5");
        draft.toggleMirror();
        draft.rotateClockwise();
        GeneratedShape template = shape(draft);
        assertTrue(draft.convertToCustom(ShapeLimits.DEFAULT));
        assertEquals(ShapeType.CUSTOM, draft.type());
        assertEquals("4", draft.text(EditorDraft.WIDTH));
        assertEquals("5", draft.text(EditorDraft.HEIGHT));
        GeneratedShape custom = shape(draft);
        assertEquals(template.mask(), custom.mask());
        assertEquals(new HashSet<>(template.positions()), new HashSet<>(custom.positions()));
        assertFalse(draft.convertToCustom(ShapeLimits.DEFAULT));
    }

    @Test
    void convertingCanBeUndoneToThePreviousCustomShape() {
        EditorDraft draft = EditorDraft.defaults();
        draft.setType(ShapeType.CUSTOM);
        draft.custom().set(1, 1, 0, true);
        draft.setType(ShapeType.CUBOID);
        draft.setText(EditorDraft.WIDTH, "5");
        assertTrue(draft.convertToCustom(ShapeLimits.DEFAULT));
        assertEquals(15, draft.custom().count());
        assertTrue(draft.undo());
        assertEquals(1, draft.custom().count());
        assertEquals(3, draft.custom().width());
        assertTrue(draft.custom().get(1, 1, 0));
    }

    @Test
    void customSizeFieldsRequestResizeInsteadOfApplyingIt() {
        EditorDraft draft = EditorDraft.defaults();
        draft.setType(ShapeType.CUSTOM);
        draft.custom().set(2, 2, 0, true);
        draft.custom().set(1, 1, 0, true);
        assertEquals(Optional.empty(), draft.requestedCustomResize(ShapeLimits.DEFAULT));
        draft.setText(EditorDraft.WIDTH, "2");
        CustomMaskDraft.ResizeCheck check = draft.requestedCustomResize(ShapeLimits.DEFAULT).orElseThrow();
        assertEquals(1, check.lostCells());
        EditorDraft.Evaluation pending = draft.evaluate(ShapeLimits.DEFAULT);
        assertTrue(pending.resizePending());
        assertFalse(pending.isValid());
        assertTrue(draft.custom().resize(check.width(), check.height(), check.depth()));
        assertTrue(draft.evaluate(ShapeLimits.DEFAULT).isValid());
    }

    @Test
    void emptyCustomIsADraftButNeverApplicable() {
        EditorDraft draft = EditorDraft.defaults();
        draft.setType(ShapeType.CUSTOM);
        EditorDraft.Evaluation evaluation = draft.evaluate(ShapeLimits.DEFAULT);
        assertFalse(evaluation.isValid());
        ShapeGenerator.Invalid invalid = (ShapeGenerator.Invalid) evaluation.result();
        assertEquals(ShapeIssue.Code.EMPTY_MASK, invalid.issues().get(0).code());
    }
}
