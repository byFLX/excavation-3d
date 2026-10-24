package com.byflx.excavation3d.editor;

/** Shared by click and wheel inputs; bounds apply even when Shift crosses an endpoint. */
public final class SizeRowStep {
    /** Only the control strip consumes the wheel; labels and validation text scroll the panel. */
    public static boolean hit(double mx, double my, int controlsLeft, int controlsRight, int rowY,
            int viewportTop, int viewportBottom) {
        return mx >= controlsLeft && mx < controlsRight && my >= rowY && my < rowY + 20
                && rowY >= viewportTop && rowY + 20 <= viewportBottom;
    }

    public static int amount(boolean shift) { return shift ? 5 : 1; }
    public static int next(int value, int direction, boolean shift, int min, int max) {
        return (int) Math.clamp((long) value + (long) Integer.signum(direction) * amount(shift), min, max);
    }
    private SizeRowStep() { }
}
