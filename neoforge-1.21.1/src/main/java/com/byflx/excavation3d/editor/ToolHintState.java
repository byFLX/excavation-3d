package com.byflx.excavation3d.editor;

import java.util.Objects;

/** Pure hint lifetime. Hidden screens defer triggers; vanilla's item name pauses visible time. */
public final class ToolHintState {
    public static final long SHOW_MS = 2500;
    public static final long FADE_MS = 500;
    private Object tool, shape;
    private boolean eligible, bypass, detailed, queued, shown, feedbackQueued;
    private long shownAt;
    private Object feedback;
    private boolean visible;
    private long highlightPausedAt = -1;

    public void confirmedChange() { feedback = null; feedbackQueued = false; queued = true; }

    /** One replacement line, using the normal lifetime/fade even while Shift is held. */
    public void showFeedback(Object message) { feedback = message; feedbackQueued = true; queued = true; }

    public Object feedback(long now) {
        return visible && shown && Math.max(0, lifetimeNow(now) - shownAt) < SHOW_MS + FADE_MS ? feedback : null;
    }

    public void update(long now, boolean eligible, Object tool, Object shape, boolean bypass,
            boolean detailed, boolean visible, int toolHighlightTimer) {
        boolean highlighted = toolHighlightTimer > 0;
        if (highlighted && highlightPausedAt < 0) highlightPausedAt = now;
        else if (!highlighted && highlightPausedAt >= 0) {
            if (shown) shownAt += Math.max(0, now - highlightPausedAt);
            highlightPausedAt = -1;
        }
        this.visible = visible && !highlighted;
        if (!eligible || (this.eligible && !Objects.equals(this.tool, tool))
                || (!feedbackQueued && shown && lifetimeNow(now) - shownAt >= SHOW_MS + FADE_MS)) {
            feedback = null;
            feedbackQueued = false;
        }
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
        else if (this.visible && queued) { shownAt = now; shown = true; queued = false; feedbackQueued = false; }
    }

    public double opacity(long now) {
        if (!eligible || !visible) return 0;
        if (detailed && feedback(now) == null) return 1;
        if (!shown) return 0;
        long elapsed = Math.max(0, lifetimeNow(now) - shownAt);
        if (elapsed <= SHOW_MS) return 1;
        return Math.clamp(1.0 - (elapsed - SHOW_MS) / (double) FADE_MS, 0, 1);
    }

    private long lifetimeNow(long now) {
        return highlightPausedAt >= 0 ? highlightPausedAt : now;
    }

    /** Above vanilla's dynamic health/armor; item-name overlap is prevented by temporal deferral. */
    public static int bottom(int height, int leftHeight, int rightHeight) {
        // Keep the operator's low position, directly above the hearts/food rows.
        return height - Math.max(39, Math.max(leftHeight, rightHeight)) - 2;
    }
}
