package com.byflx.excavation3d.shape;

/**
 * Effective size limits for a generated shape (plan §4). The technical maximums are the
 * version 1 defaults; a server may only lower them, never raise them.
 */
public record ShapeLimits(int maxAxis, int maxSelected) {
    public static final int TECHNICAL_MAX_AXIS = 32;
    public static final int TECHNICAL_MAX_SELECTED = 4096;
    public static final ShapeLimits DEFAULT = new ShapeLimits(TECHNICAL_MAX_AXIS, TECHNICAL_MAX_SELECTED);

    public ShapeLimits {
        if (maxAxis < 1 || maxAxis > TECHNICAL_MAX_AXIS) {
            throw new IllegalArgumentException("maxAxis must be in 1.." + TECHNICAL_MAX_AXIS + ": " + maxAxis);
        }
        if (maxSelected < 1 || maxSelected > TECHNICAL_MAX_SELECTED) {
            throw new IllegalArgumentException(
                    "maxSelected must be in 1.." + TECHNICAL_MAX_SELECTED + ": " + maxSelected);
        }
    }
}
