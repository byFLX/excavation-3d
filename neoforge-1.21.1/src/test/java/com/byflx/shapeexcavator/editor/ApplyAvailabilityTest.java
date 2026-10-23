package com.byflx.shapeexcavator.editor;

import static org.junit.jupiter.api.Assertions.*;

import com.byflx.shapeexcavator.preview.PreviewGesture;
import com.byflx.shapeexcavator.shape.ShapeLimits;
import org.junit.jupiter.api.Test;

class ApplyAvailabilityTest {
    @Test void customPreviewClickLeavesApplyReadyAndOnlyServerAcceptanceClearsDirtyState() {
        var draft = EditorDraft.defaults();
        assertTrue(draft.convertToCustom(ShapeLimits.DEFAULT));
        draft.custom().resize(5, 3, 1);
        draft.syncCustomTexts();
        var session = new SessionHandshake();
        assertTrue(session.onReply(session.start(), true, 42));
        var gesture = new PreviewGesture();
        var saved = new DraftSaveState();
        gesture.press(0, 100, 80, true);
        if (gesture.release(0, 100, 80, true, 1000) == PreviewGesture.Release.CLICK) {
            assertTrue(draft.custom().set(4, 1, 0, true));
            saved.edited();
        }
        assertEquals(-1, gesture.button());
        assertEquals(ApplyAvailability.READY,
                ApplyAvailability.of(draft.evaluate(ShapeLimits.DEFAULT), false, false, session.sessionId() != 0));
        long submitted = saved.submit();
        assertTrue(saved.isDirty());
        assertEquals(ApplyAvailability.WAITING_FOR_RESULT,
                ApplyAvailability.of(draft.evaluate(ShapeLimits.DEFAULT), false, true, true));
        assertTrue(saved.accepted(submitted));
        assertFalse(saved.isDirty());
    }

    @Test void invalidFieldsAndPendingShrinkBlockApplyUntilCorrected() {
        var draft = EditorDraft.defaults();
        draft.setText(EditorDraft.WIDTH, "");
        assertEquals(ApplyAvailability.INVALID_DRAFT,
                ApplyAvailability.of(draft.evaluate(ShapeLimits.DEFAULT), false, false, true));
        draft.setText(EditorDraft.WIDTH, "3");
        assertEquals(ApplyAvailability.RESIZE_PENDING,
                ApplyAvailability.of(draft.evaluate(ShapeLimits.DEFAULT), true, false, true));
        assertEquals(ApplyAvailability.READY,
                ApplyAvailability.of(draft.evaluate(ShapeLimits.DEFAULT), false, false, true));
    }

    @Test void lateSessionReplyEnablesApplyWithoutLosingTheDraft() {
        var draft = EditorDraft.defaults();
        var session = new SessionHandshake();
        int request = session.start();
        assertEquals(ApplyAvailability.SESSION_UNAVAILABLE,
                ApplyAvailability.of(draft.evaluate(ShapeLimits.DEFAULT), false, false, false));
        assertTrue(session.onReply(request, true, 42));
        assertEquals(ApplyAvailability.READY,
                ApplyAvailability.of(draft.evaluate(ShapeLimits.DEFAULT), false, false, session.sessionId() != 0));
    }
}
