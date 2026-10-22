package com.byflx.shapeexcavator.client.screen;

import net.minecraft.util.Mth;

/**
 * View state of the editor's 3D preview (plan §5). Purely visual: it never changes shape data and
 * never triggers shape generation. {@code yaw} turns around the vertical axis, {@code elevation}
 * looks down from above (90 = top view).
 */
public final class PreviewCamera {
    private static final float DEFAULT_YAW = -35.0F;
    private static final float DEFAULT_ELEVATION = 25.0F;

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
        yaw = Mth.wrapDegrees(yaw + (float) dx * 0.8F);
        elevation = Mth.clamp(elevation + (float) dy * 0.8F, -90.0F, 90.0F);
    }

    public void scroll(double amount) {
        zoom = Mth.clamp(zoom * (float) Math.pow(1.15, amount), 0.3F, 4.0F);
    }

    public void front() {
        set(0.0F, 0.0F);
    }

    /** Looks at the right side (local +width face). */
    public void side() {
        set(-90.0F, 0.0F);
    }

    public void top() {
        set(0.0F, 90.0F);
    }

    public void reset() {
        set(DEFAULT_YAW, DEFAULT_ELEVATION);
        zoom = 1.0F;
    }

    private void set(float newYaw, float newElevation) {
        yaw = newYaw;
        elevation = newElevation;
    }
}
