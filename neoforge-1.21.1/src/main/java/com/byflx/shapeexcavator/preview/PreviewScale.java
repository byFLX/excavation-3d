package com.byflx.shapeexcavator.preview;

/**
 * Block scale of the editor's 3D preview in pixels per block (request 2026-10-22). The scale stays
 * fixed while the shape changes, so a size change shows as a size change; it only shrinks when the
 * shape no longer fits, and it never zooms in by itself.
 */
public final class PreviewScale {
    /** Largest scale at open and reset, so a single block does not fill the whole panel. */
    public static final float MAX_BLOCK_PX = 16.0F;
    /** Free space around the shape, in blocks, shared by both sides. */
    private static final float MARGIN_BLOCKS = 3.0F;

    private PreviewScale() {
    }

    /** The scale at which the shape fits the panel from every camera angle, with a margin around it. */
    public static float fit(int panelWidth, int panelHeight, int width, int height, int depth) {
        return (float) (panelSize(panelWidth, panelHeight) / (diagonal(width, height, depth) + MARGIN_BLOCKS));
    }

    /**
     * Whether the shape, centred in the panel, is fully visible from every camera angle at
     * {@code displayed} pixels per block. Its projection never reaches further than half the
     * diagonal from the centre. No margin here: the margin only applies when the scale is re-fitted.
     */
    public static boolean fits(float displayed, int panelWidth, int panelHeight, int width, int height, int depth) {
        return diagonal(width, height, depth) * displayed <= panelSize(panelWidth, panelHeight);
    }

    public static float initial(int panelWidth, int panelHeight, int width, int height, int depth) {
        return Math.min(fit(panelWidth, panelHeight, width, height, depth), MAX_BLOCK_PX);
    }

    /**
     * Keeps {@code current} while the shape {@link #fits}; otherwise the scale at which it fits again
     * with the margin of {@link #fit}.
     */
    public static float next(float current, int panelWidth, int panelHeight, int width, int height, int depth) {
        return next(current, 1.0F, panelWidth, panelHeight, width, height, depth);
    }

    /**
     * Like {@link #next(float, int, int, int, int, int)}, but decides on the displayed scale
     * {@code current * zoom}: after the user zoomed out, a shape that still fits on screen keeps its
     * scale. A deliberate zoom-in is not counted, so it never makes a resize shrink the scale.
     */
    public static float next(float current, float zoom, int panelWidth, int panelHeight, int width, int height,
            int depth) {
        if (!(current > 0.0F)) {
            return initial(panelWidth, panelHeight, width, height, depth);
        }
        float userZoomOut = zoom > 0.0F ? Math.min(zoom, 1.0F) : 1.0F;
        if (fits(current * userZoomOut, panelWidth, panelHeight, width, height, depth)) {
            return current;
        }
        return Math.min(current, fit(panelWidth, panelHeight, width, height, depth) / userZoomOut);
    }

    private static double diagonal(int width, int height, int depth) {
        return Math.sqrt((double) width * width + (double) height * height + (double) depth * depth);
    }

    private static int panelSize(int panelWidth, int panelHeight) {
        return Math.max(1, Math.min(panelWidth, panelHeight));
    }
}
