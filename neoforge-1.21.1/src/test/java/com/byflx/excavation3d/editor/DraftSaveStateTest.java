package com.byflx.excavation3d.editor;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DraftSaveStateTest {
    @Test
    void freshDraftIsClean() {
        assertFalse(new DraftSaveState().isDirty());
    }

    @Test
    void confirmationOfTheCurrentDraftMakesItClean() {
        DraftSaveState state = new DraftSaveState();
        state.edited();
        assertTrue(state.isDirty());
        long submitted = state.submit();
        assertTrue(state.accepted(submitted));
        assertFalse(state.isDirty());
    }

    @Test
    void editsMadeWhileAwaitingTheReplyStayUnsaved() {
        DraftSaveState state = new DraftSaveState();
        state.edited(); // width 3
        long submitted = state.submit();
        state.edited(); // width 7 during network latency
        assertFalse(state.accepted(submitted));
        assertTrue(state.isDirty());

        long second = state.submit();
        assertTrue(state.accepted(second));
        assertFalse(state.isDirty());
    }

    @Test
    void lateOrRepeatedConfirmationsNeverMarkNewerEditsSaved() {
        DraftSaveState state = new DraftSaveState();
        state.edited();
        long first = state.submit();
        state.edited();
        long second = state.submit();
        assertTrue(state.accepted(second));
        // An older confirmation arriving afterwards must not move the saved point backwards.
        assertTrue(state.accepted(first));
        state.edited();
        assertFalse(state.accepted(second));
        assertTrue(state.isDirty());
    }

    @Test
    void rejectedSubmissionKeepsTheDraftDirty() {
        DraftSaveState state = new DraftSaveState();
        state.edited();
        state.submit();
        // A rejection simply never calls accepted().
        assertTrue(state.isDirty());
    }
}
