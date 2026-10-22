package com.byflx.shapeexcavator.net;

import java.util.Objects;
import java.util.UUID;

/**
 * Whether an Apply or Remove request may change the tool its editor session is bound to (plan §2a,
 * §9). The session binds player, hotbar slot, the stack instance in that slot and the tool id of
 * its shape, which is null while the tool has none. {@link EditSessions} gathers the facts on the
 * server thread; the decision itself is pure.
 */
public enum SessionBinding {
    ACCEPT(null),
    /** The request names another slot, or the bound slot is no longer selected. */
    SLOT_CHANGED("tool_changed"),
    /** Another stack lies in the slot, even if it looks the same. */
    STACK_CHANGED("tool_changed"),
    /** The item is no longer in {@code shapeexcavator:shape_tools}, e.g. after a datapack reload. */
    NOT_SHAPE_TOOL("not_shape_tool"),
    /** The shape was replaced or removed elsewhere since the session began. */
    TOOL_ID_CHANGED("tool_changed"),
    REVISION("revision");

    private final String rejectKey;

    SessionBinding(String rejectKey) {
        this.rejectKey = rejectKey;
    }

    /** Suffix of the {@code message.shapeexcavator.reject.*} key; null for {@link #ACCEPT}. */
    public String rejectKey() {
        return rejectKey;
    }

    /**
     * @param slotSelected     the request's slot is the session's slot and still the selected one
     * @param sameStack        the slot holds the very stack instance the session was bound to
     * @param shapeTool        that stack is in {@code shapeexcavator:shape_tools}
     * @param boundToolId      the tool id the session is bound to, null if the tool had no shape
     * @param currentToolId    the stack's current tool id, null if it has no shape
     * @param expectedRevision the revision the editor last saw
     * @param currentRevision  the stack's current revision, 0 if it has no shape
     */
    public static SessionBinding check(boolean slotSelected, boolean sameStack, boolean shapeTool,
            UUID boundToolId, UUID currentToolId, long expectedRevision, long currentRevision) {
        if (!slotSelected) {
            return SLOT_CHANGED;
        }
        if (!sameStack) {
            return STACK_CHANGED;
        }
        if (!shapeTool) {
            return NOT_SHAPE_TOOL;
        }
        if (!Objects.equals(boundToolId, currentToolId)) {
            return TOOL_ID_CHANGED;
        }
        if (expectedRevision != currentRevision) {
            return REVISION;
        }
        return ACCEPT;
    }
}
