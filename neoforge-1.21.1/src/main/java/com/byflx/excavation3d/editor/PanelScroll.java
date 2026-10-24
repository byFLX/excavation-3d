package com.byflx.excavation3d.editor;

/** Scrollbar geometry shared by drawing, hit testing and pointer dragging. */
public record PanelScroll(int top, int bottom, int maximum) {
    public int thumbHeight() {
        int track = bottom - top;
        return Math.min(track, Math.max(10, track * track / (track + maximum)));
    }

    public int thumbY(int scroll) {
        return top + (bottom - top - thumbHeight()) * scroll / Math.max(1, maximum);
    }

    public int dragged(double mouseY, double grabOffset) {
        int travel = bottom - top - thumbHeight();
        return travel <= 0 ? 0 : (int) Math.clamp(Math.round(
                (mouseY - grabOffset - top) * maximum / travel), 0, maximum);
    }
}
