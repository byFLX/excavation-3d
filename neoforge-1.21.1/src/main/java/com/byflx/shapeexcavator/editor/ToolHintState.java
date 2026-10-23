package com.byflx.shapeexcavator.editor;

import java.util.Objects;

/** Pure event-driven hint lifetime. Hidden screens defer a trigger until the hint can be seen. */
public final class ToolHintState {
    public static final long SHOW_MS = 2500;
    public static final long FADE_MS = 500;
    private Object tool, shape;
    private boolean eligible, bypass, detailed, queued, shown;
    private long shownAt;

    public void confirmedChange() { queued = true; }

    public void update(long now, boolean eligible, Object tool, Object shape, boolean bypass,
            boolean detailed, boolean visible) {
        if (eligible && (!this.eligible || !Objects.equals(this.tool, tool)
                || !Objects.equals(this.shape, shape) || this.bypass != bypass || this.detailed != detailed)) {
            queued = true;
        }
        this.eligible = eligible;
        this.tool = tool;
        this.shape = shape;
        this.bypass = bypass;
        this.detailed = detailed;
        if (!eligible) { queued = false; shown = false; }
        else if (visible && queued) { shownAt = now; shown = true; queued = false; }
    }

    public double opacity(long now) {
        if (!eligible) return 0;
        if (detailed) return 1;
        if (!shown) return 0;
        long elapsed = Math.max(0, now - shownAt);
        if (elapsed <= SHOW_MS) return 1;
        return Math.clamp(1.0 - (elapsed - SHOW_MS) / (double) FADE_MS, 0, 1);
    }

    /** Above vanilla's dynamic health/armor, item name and action bar, with a readable gap. */
    public static int bottom(int height, int leftHeight, int rightHeight) {
        return height - Math.max(86, Math.max(leftHeight, rightHeight) + 30);
    }
}
