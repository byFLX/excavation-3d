package com.byflx.excavation3d.editor;

import static org.junit.jupiter.api.Assertions.*;

import com.byflx.excavation3d.preview.PreviewPicking;
import com.byflx.excavation3d.shape.*;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

class CustomOrientationTest {
    @Test void customIconKeepsTheTemplatesModeAndWorldSelectionForEveryHitAndFacing() {
        for (ShapeType type : ShapeType.values()) {
            if (type == ShapeType.CUSTOM) continue;
            ShapeConfig original = ShapeConfig.of(ShapeTemplates.defaults(type));
            EditorDraft draft = EditorDraft.fromConfig(original);
            assertTrue(draft.convertToCustom(ShapeLimits.DEFAULT));
            ShapeConfig converted = draft.evaluate(ShapeLimits.DEFAULT).config();
            assertEquals(original.orientationMode(), converted.orientationMode());
            var before = ((ShapeGenerator.Valid) ShapeGenerator.generate(original, ShapeLimits.DEFAULT)).shape();
            var after = draft.evaluate(ShapeLimits.DEFAULT).shape().orElseThrow();
            for (Direction face : Direction.values()) {
                for (Direction facing : new Direction[] {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
                    var originalAxes = Orientation.orient(face, facing, original.orientationMode());
                    var convertedAxes = Orientation.orient(face, facing, converted.orientationMode());
                    assertEquals(before.worldOffsets(originalAxes), after.worldOffsets(convertedAxes));
                }
            }
        }
    }

    @Test void firstPreviewEditKeepsOrientationThroughUndoRedoAndTypeSwitching() {
        for (ShapeType type : new ShapeType[] {ShapeType.STAIR, ShapeType.CORRIDOR}) {
            EditorDraft draft = EditorDraft.fromConfig(ShapeConfig.of(ShapeTemplates.defaults(type)));
            ShapeMask before = draft.evaluate(ShapeLimits.DEFAULT).shape().orElseThrow().mask();
            Cell edge = before.positions().stream().filter(c -> c.y() == before.height() - 1).findFirst().orElseThrow();
            assertEquals(EditorDraft.PreviewEdit.CHANGED, draft.editTemplatePreview(
                    new PreviewPicking.Hit(edge, PreviewPicking.Face.UP, true, 1), true, ShapeLimits.DEFAULT));
            assertEquals(Orientation.Mode.PLAYER_HORIZONTAL, draft.evaluate(ShapeLimits.DEFAULT).config().orientationMode());
            assertTrue(draft.undo());
            assertEquals(type, draft.type());
            assertTrue(draft.redo());
            assertEquals(Orientation.Mode.PLAYER_HORIZONTAL, draft.evaluate(ShapeLimits.DEFAULT).config().orientationMode());
            draft.setType(ShapeType.CUBOID);
            draft.setType(ShapeType.CUSTOM);
            assertEquals(Orientation.Mode.PLAYER_HORIZONTAL, draft.evaluate(ShapeLimits.DEFAULT).config().orientationMode());
            draft.resetDefaults();
            assertEquals(Orientation.Mode.HIT_FACE, draft.evaluate(ShapeLimits.DEFAULT).config().orientationMode());
            assertTrue(draft.undo());
            assertEquals(Orientation.Mode.PLAYER_HORIZONTAL, draft.evaluate(ShapeLimits.DEFAULT).config().orientationMode());
        }
    }
}
