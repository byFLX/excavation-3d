package com.byflx.excavation3d.client.screen;

/**
 * View state of the editor's 3D preview (plan §5). Purely visual: it never changes shape data and
 * never triggers shape generation. {@code yaw} turns around the vertical axis, {@code elevation}
 * looks down from above (90 = top view). Dragging rotates, the mouse wheel zooms and a double-click
 * resets; the zoom multiplies the renderer's fixed block scale.
 */
public final class PreviewCamera {
    private static final float DEFAULT_YAW = -35.0F;
    private static final float DEFAULT_ELEVATION = 25.0F;
    public static final float MIN_ELEVATION = 10.0F;
    public static final float MAX_ELEVATION = 65.0F;

    private float yaw = DEFAULT_YAW;
    private float elevation = DEFAULT_ELEVATION;
    private float zoom = 1.0F;

    public float yaw() {
        return yaw;
    }

    public float elevation() {
        return elevation;
    }

    public float zoom() {
        return zoom;
    }

    public void drag(double dx, double dy) {
        yaw = (float) ((yaw + dx * 0.8) % 360.0);
        if (yaw >= 180) yaw -= 360;
        if (yaw < -180) yaw += 360;
        elevation = Math.clamp(elevation + (float) dy * 0.8F, MIN_ELEVATION, MAX_ELEVATION);
    }

    public void scroll(double amount) {
        zoom = Math.clamp(zoom * (float) Math.pow(1.15, amount), 0.3F, 4.0F);
    }

    /** Double-click in the preview: default angle and no user zoom. */
    public void reset() {
        yaw = DEFAULT_YAW;
        elevation = DEFAULT_ELEVATION;
        zoom = 1.0F;
    }
}
