package com.byflx.excavation3d.editor;

import static org.junit.jupiter.api.Assertions.*;

import com.byflx.excavation3d.shape.*;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class FavoritesOverviewTest {
    private static final ShapeConfig BOX = ShapeConfig.of(new ShapeParams.Cuboid(3, 3, 1));

    private static ShapeConfig config(EditorDraft draft) {
        return draft.evaluate(ShapeLimits.DEFAULT).config();
    }

    @Test void rowsKeepWheelOrderAndOnlyTheFirstEqualConfigHasAGoldMarker() {
        var stored = Map.of("Zulu", BOX.withRevision(2), "Alpha", BOX,
                "Middle", ShapeConfig.of(new ShapeParams.Plus(3, 3, 1, 1)));
        var rows = FavoritesOverview.rows(List.of("Zulu", "Middle", "Alpha"),
                name -> Optional.ofNullable(stored.get(name)), BOX.withRevision(99));
        assertEquals(List.of("Zulu", "Middle", "Alpha"), rows.stream().map(FavoritesOverview.Row::name).toList());
        assertEquals(List.of(true, false, false), rows.stream().map(FavoritesOverview.Row::active).toList());
        assertEquals("Box 3 × 3 × 1", rows.getFirst().summary());
        assertEquals(ShapeType.CUBOID, rows.getFirst().config().type());
    }

    @Test void invalidOrUnmatchedDraftsHaveNoMarkerAndMissingRowsAreSkipped() {
        var rows = FavoritesOverview.rows(List.of("Missing", "Box"),
                name -> name.equals("Box") ? Optional.of(BOX) : Optional.empty(), null);
        assertEquals(1, rows.size());
        assertFalse(rows.getFirst().active());
        assertFalse(FavoritesOverview.rows(List.of("Box"), name -> Optional.of(BOX),
                BOX.withTransform(new ShapeTransform(1, false))).getFirst().active());
        assertTrue(FavoritesOverview.rows(List.of(), name -> Optional.of(BOX), BOX).isEmpty());
    }

    @Test void summariesDescribeEveryTypeAndTransformedExtentsWithoutGeneratingShapes() {
        assertEquals("Plus 5 × 3 × 2", FavoritesOverview.summary(ShapeConfig.of(new ShapeParams.Plus(5, 3, 2, 1))));
        assertEquals("Stairs 2 × 8 × 12", FavoritesOverview.summary(ShapeConfig.of(new ShapeParams.Stair(2, 4, 3, 2, false))));
        assertEquals("Corridor 2 × 9 × 12", FavoritesOverview.summary(ShapeConfig.of(new ShapeParams.Corridor(2, 3, 4, 3, 2, false))));
        ShapeConfig custom = ShapeConfig.of(new ShapeParams.Custom(ShapeMask.full(2, 4, 1, new Cell(0, 0, 0))),
                new ShapeTransform(1, true));
        assertEquals("Custom 4 × 2 × 1", FavoritesOverview.summary(custom));
        assertEquals("Box 100 × 200 × 300", FavoritesOverview.summary(ShapeConfig.of(new ShapeParams.Cuboid(100, 200, 300))));
    }

    @Test void secondDeleteClickMustMatchTheNameAndArriveBeforeThreeSeconds() {
        var state = new FavoritesOverview();
        assertFalse(state.deleteClick("Box", 100));
        assertEquals("Box", state.pendingDelete(3099));
        assertTrue(state.deleteClick("Box", 3099));
        assertNull(state.pendingDelete(3100));
        assertFalse(state.deleteClick("Box", 4000));
        assertFalse(state.deleteClick("Box", 7000));
        assertFalse(state.deleteClick("Other", 7100));
        assertEquals("Other", state.pendingDelete(7101));
        assertFalse(state.deleteClick("Box", 7200));
        assertTrue(state.deleteClick("Box", 7201));
    }

    @Test void closeScrollOrAClockResetCannotLeaveDeletionArmed() {
        var state = new FavoritesOverview();
        state.deleteClick("Box", 100);
        state.cancelDelete();
        assertFalse(state.deleteClick("Box", 101));
        assertNull(state.pendingDelete(99));
        assertFalse(state.deleteClick("Box", 102));
        assertNull(state.pendingDelete(3102));
    }

    @Test void selectingPrefillsTheNameMarksDirtyAndLoadsAsOneUndoStep() {
        var state = new FavoritesOverview();
        state.setName("Previously typed");
        state.deleteClick("Other", 10);
        var saveState = new DraftSaveState();
        var draft = EditorDraft.defaults();
        ShapeConfig original = config(draft);
        state.select(new FavoritesOverview.Row("My box", BOX, "", false), draft, saveState);
        assertEquals("My box", state.name());
        assertEquals(BOX, config(draft));
        assertTrue(saveState.isDirty());
        assertNull(state.pendingDelete(11));
        assertTrue(draft.undo());
        assertEquals(original, config(draft));
        assertFalse(draft.canUndo());
        assertTrue(draft.redo());
        assertEquals(BOX, config(draft));
        state.setName("New name");
        assertEquals("New name", state.name());
    }

    @Test void loadingCustomThenAnotherTypeKeepsPriorEditsAndUndoRestoresRawInvalidFields() {
        var draft = EditorDraft.defaults();
        draft.setText(EditorDraft.WIDTH, "");
        ShapeConfig custom = ShapeConfig.of(new ShapeParams.Custom(
                ShapeMask.full(2, 4, 1, new Cell(1, 2, 0)), Orientation.Mode.PLAYER_HORIZONTAL),
                new ShapeTransform(3, true));
        draft.loadFavorite(custom);
        assertEquals(custom, config(draft));
        draft.loadFavorite(BOX);
        assertEquals(BOX, config(draft));
        assertTrue(draft.undo());
        assertEquals(custom, config(draft));
        assertTrue(draft.undo());
        assertEquals(ShapeType.CUBOID, draft.type());
        assertEquals("", draft.text(EditorDraft.WIDTH));
        assertTrue(draft.undo());
        assertFalse(draft.canUndo());
    }

    @Test void loadAfterUndoClearsRedoAndPreservesTheOtherTypesValues() {
        var draft = EditorDraft.defaults();
        draft.setType(ShapeType.STAIR);
        draft.setText(EditorDraft.WIDTH, "7");
        draft.setStairAscending(false);
        draft.setType(ShapeType.CUSTOM);
        draft.custom().set(0, 0, 0, true);
        assertTrue(draft.undo());
        assertTrue(draft.canRedo());
        draft.loadFavorite(BOX);
        assertFalse(draft.canRedo());
        draft.setType(ShapeType.STAIR);
        assertEquals("7", draft.text(EditorDraft.WIDTH));
        assertFalse(draft.stairAscending());
    }

    @Test void legacyTransformedTemplateLoadsWithTheSameGeometryAndUndoesAtomically() {
        var draft = EditorDraft.defaults();
        ShapeConfig before = config(draft);
        ShapeConfig legacy = ShapeConfig.of(new ShapeParams.Cuboid(2, 4, 1), new ShapeTransform(1, true));
        draft.loadFavorite(legacy);
        assertEquals(ShapeType.CUSTOM, draft.type());
        assertEquals(((ShapeGenerator.Valid) ShapeGenerator.generate(legacy, ShapeLimits.DEFAULT)).shape(),
                draft.evaluate(ShapeLimits.DEFAULT).shape().orElseThrow());
        assertTrue(draft.undo());
        assertEquals(before, config(draft));
        assertFalse(draft.canUndo());
    }

    @Test void evenSelectingTheCurrentShapeIsOneExplicitDirtyUndoableAction() {
        var draft = EditorDraft.fromConfig(BOX);
        var saveState = new DraftSaveState();
        var state = new FavoritesOverview();
        state.select(new FavoritesOverview.Row("Same", BOX, "", true), draft, saveState);
        assertTrue(saveState.isDirty());
        assertEquals("Same", state.name());
        assertTrue(draft.undo());
        assertEquals(BOX, config(draft));
        assertFalse(draft.canUndo());
    }
}
