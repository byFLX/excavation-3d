package com.byflx.excavation3d.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The server's check before Apply or Remove changes a tool ({@link EditSessions}, plan §2a). */
class SessionBindingTest {
    private static final UUID TOOL = UUID.fromString("00000000-0000-0000-0000-000000000042");
    private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-000000000043");

    @Test
    void firstApplyOnAToolWithoutShapeIsAccepted() {
        // Opening the editor wrote nothing: no tool id on either side, revision 0.
        assertEquals(SessionBinding.ACCEPT, SessionBinding.check(true, true, true, null, null, 0L, 0L));
    }

    @Test
    void changeOfAConfiguredToolIsAccepted() {
        assertEquals(SessionBinding.ACCEPT, SessionBinding.check(true, true, true, TOOL, TOOL, 3L, 3L));
    }

    @Test
    void wrongOrDeselectedSlotIsRejected() {
        assertEquals(SessionBinding.SLOT_CHANGED, SessionBinding.check(false, true, true, TOOL, TOOL, 3L, 3L));
        assertEquals("tool_changed", SessionBinding.SLOT_CHANGED.rejectKey());
    }

    @Test
    void anotherStackInTheSlotIsRejectedEvenWithoutShapes() {
        // Two vanilla pickaxes have no tool id; only the stack instance tells them apart.
        assertEquals(SessionBinding.STACK_CHANGED, SessionBinding.check(true, false, true, null, null, 0L, 0L));
        assertEquals("tool_changed", SessionBinding.STACK_CHANGED.rejectKey());
    }

    @Test
    void itemOutsideTheTagIsRejected() {
        assertEquals(SessionBinding.NOT_SHAPE_TOOL, SessionBinding.check(true, true, false, TOOL, TOOL, 3L, 3L));
        assertEquals("not_shape_tool", SessionBinding.NOT_SHAPE_TOOL.rejectKey());
    }

    @Test
    void toolIdMismatchIsRejected() {
        assertEquals(SessionBinding.TOOL_ID_CHANGED, SessionBinding.check(true, true, true, TOOL, OTHER, 3L, 3L));
        // Shape removed elsewhere after the session began.
        assertEquals(SessionBinding.TOOL_ID_CHANGED, SessionBinding.check(true, true, true, TOOL, null, 3L, 0L));
        // Shape applied elsewhere to a tool that had none when the session began.
        assertEquals(SessionBinding.TOOL_ID_CHANGED, SessionBinding.check(true, true, true, null, TOOL, 0L, 1L));
    }

    @Test
    void revisionMismatchIsRejected() {
        assertEquals(SessionBinding.REVISION, SessionBinding.check(true, true, true, TOOL, TOOL, 2L, 3L));
        assertEquals("revision", SessionBinding.REVISION.rejectKey());
    }

    @Test
    void removeOfADamagedShapeIsAccepted() {
        // Revision > 0 with an unreadable config: same id and revision, so Remove may delete it.
        assertEquals(SessionBinding.ACCEPT, SessionBinding.check(true, true, true, TOOL, TOOL, 5L, 5L));
    }

    @Test
    void applyAfterRemoveInTheSameSessionIsAccepted() {
        // After a Remove the session's tool id is cleared and the editor's revision is 0.
        assertEquals(SessionBinding.ACCEPT, SessionBinding.check(true, true, true, null, null, 0L, 0L));
    }

    @Test
    void checksRunInOrder() {
        assertEquals(SessionBinding.SLOT_CHANGED, SessionBinding.check(false, false, false, TOOL, OTHER, 1L, 2L));
        assertEquals(SessionBinding.STACK_CHANGED, SessionBinding.check(true, false, false, TOOL, OTHER, 1L, 2L));
        assertNull(SessionBinding.ACCEPT.rejectKey());
    }
}
