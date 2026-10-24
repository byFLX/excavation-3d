package com.byflx.excavation3d.shape;

import java.util.Objects;

/**
 * Parameter presets. Surfaces and lines are cuboids with fixed dimensions, not separate geometry
 * (plan §6.1).
 */
public final class ShapeTemplates {
    public enum LineAxis {
        WIDTH,
        HEIGHT,
        DEPTH
    }

    private ShapeTemplates() {
    }

    public static ShapeParams.Cuboid cuboid(int width, int height, int depth) {
        return new ShapeParams.Cuboid(width, height, depth);
    }

    /** A cuboid with depth 1. */
    public static ShapeParams.Cuboid surface(int width, int height) {
        return new ShapeParams.Cuboid(width, height, 1);
    }

    /** A cuboid whose other two dimensions are 1. */
    public static ShapeParams.Cuboid line(int length, LineAxis axis) {
        Objects.requireNonNull(axis, "axis");
        return switch (axis) {
            case WIDTH -> new ShapeParams.Cuboid(length, 1, 1);
            case HEIGHT -> new ShapeParams.Cuboid(1, length, 1);
            case DEPTH -> new ShapeParams.Cuboid(1, 1, length);
        };
    }

    /**
     * An empty custom grid with the cuboid default anchor. Valid only as an editor draft:
     * {@link ShapeValidator} reports {@link ShapeIssue.Code#EMPTY_MASK} until a cell is selected.
     */
    public static ShapeParams.Custom emptyCustom(int width, int height, int depth) {
        return new ShapeParams.Custom(
                ShapeMask.empty(width, height, depth, new Cell((width - 1) / 2, (height - 1) / 2, 0)));
    }

    /** Starting parameters when the editor switches to {@code type}. CUSTOM starts as an empty draft. */
    public static ShapeParams defaults(ShapeType type) {
        Objects.requireNonNull(type, "type");
        return switch (type) {
            case CUBOID -> new ShapeParams.Cuboid(3, 3, 1);
            case PLUS -> new ShapeParams.Plus(3, 3, 1, 1);
            case STAIR -> new ShapeParams.Stair(3, 4, 1, 1, true);
            case CORRIDOR -> new ShapeParams.Corridor(3, 3, 4, 1, 1, true);
            case CUSTOM -> emptyCustom(3, 3, 1);
        };
    }
}
