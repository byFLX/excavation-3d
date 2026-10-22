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

    /** The largest scale at which the shape fits the panel from every camera angle. */
    public static float fit(int panelWidth, int panelHeight, int width, int height, int depth) {
        double diagonal = Math.sqrt((double) width * width + (double) height * height + (double) depth * depth);
        return (float) (Math.max(1, Math.min(panelWidth, panelHeight)) / (diagonal + MARGIN_BLOCKS));
    }

    public static float initial(int panelWidth, int panelHeight, int width, int height, int depth) {
        return Math.min(fit(panelWidth, panelHeight, width, height, depth), MAX_BLOCK_PX);
    }

    /** Keeps {@code current} while the shape fits; otherwise the scale at which it fits again. */
    public static float next(float current, int panelWidth, int panelHeight, int width, int height, int depth) {
        if (!(current > 0.0F)) {
            return initial(panelWidth, panelHeight, width, height, depth);
        }
        return Math.min(current, fit(panelWidth, panelHeight, width, height, depth));
    }
}
