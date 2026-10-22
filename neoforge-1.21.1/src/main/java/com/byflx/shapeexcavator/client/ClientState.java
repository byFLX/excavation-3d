package com.byflx.shapeexcavator.client;

import com.byflx.shapeexcavator.client.screen.ShapeEditorScreen;
import com.byflx.shapeexcavator.shape.ShapeLimits;

/** Client-only state: the server's limits, the open editor and the single-block key. Client thread only. */
public final class ClientState {
    private static ShapeLimits limits = ShapeLimits.DEFAULT;
    private static ShapeEditorScreen editor;
    private static boolean bypassActive;

    private ClientState() {
    }

    public static ShapeLimits limits() {
        return limits;
    }

    /** Applies limits sent by the server; values outside the technical range fall back to the defaults. */
    public static void setLimits(int maxAxis, int maxSelected) {
        try {
            limits = new ShapeLimits(maxAxis, maxSelected);
        } catch (IllegalArgumentException e) {
            limits = ShapeLimits.DEFAULT;
        }
    }

    /** The editor that receives session and apply replies, also while a confirmation dialog covers it. */
    public static ShapeEditorScreen editor() {
        return editor;
    }

    public static void setEditor(ShapeEditorScreen screen) {
        editor = screen;
    }

    public static boolean bypassActive() {
        return bypassActive;
    }

    static void setBypassActive(boolean active) {
        bypassActive = active;
    }

    static void reset() {
        limits = ShapeLimits.DEFAULT;
        editor = null;
        bypassActive = false;
    }
}
