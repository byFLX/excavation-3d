package com.byflx.shapeexcavator.client;

import com.byflx.shapeexcavator.client.screen.ShapeEditorScreen;
import com.byflx.shapeexcavator.net.ApplyResultPayload;
import com.byflx.shapeexcavator.net.EditSessionPayload;
import com.byflx.shapeexcavator.net.LimitsPayload;

/** Client side of the editor protocol. Called on the client main thread (enqueued by {@code ModNetwork}). */
public final class ClientPayloadHandler {
    private ClientPayloadHandler() {
    }

    public static void onEditSession(EditSessionPayload payload) {
        ClientState.setLimits(payload.maxAxis(), payload.maxSelected());
        QuickShapeAction.onSession(payload);
        ShapeEditorScreen editor = ClientState.editor();
        if (editor != null) {
            editor.onSession(payload);
        }
    }

    public static void onApplyResult(ApplyResultPayload payload) {
        ClientState.removalResult(payload.sessionId(), payload.accepted());
        QuickShapeAction.onResult(payload);
        ShapeEditorScreen editor = ClientState.editor();
        if (editor != null) {
            editor.onApplyResult(payload);
        }
    }

    public static void onLimits(LimitsPayload payload) {
        ClientState.setLimits(payload.maxAxis(), payload.maxSelected());
    }
}
