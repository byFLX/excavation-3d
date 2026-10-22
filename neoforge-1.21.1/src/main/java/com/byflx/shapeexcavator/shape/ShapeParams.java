package com.byflx.shapeexcavator.shape;

import java.util.Objects;

/**
 * Raw, unvalidated shape parameters (plan §6). Any int is representable so that editor drafts and
 * decoded data can be checked by {@link ShapeValidator}; nothing here throws for bad numbers.
 */
public sealed interface ShapeParams {
    ShapeType type();

    record Cuboid(int width, int height, int depth) implements ShapeParams {
        @Override
        public ShapeType type() {
            return ShapeType.CUBOID;
        }
    }

    record Plus(int width, int height, int depth, int arm) implements ShapeParams {
        @Override
        public ShapeType type() {
            return ShapeType.PLUS;
        }
    }

    /** Solid stepped mass: depth = steps × stepDepth, height = steps × stepHeight. */
    record Stair(int width, int steps, int stepDepth, int stepHeight, boolean ascending) implements ShapeParams {
        @Override
        public ShapeType type() {
            return ShapeType.STAIR;
        }
    }

    /** Walkable stair corridor: height = clearance + (steps − 1) × stepHeight, depth = steps × stepDepth. */
    record Corridor(int width, int clearance, int steps, int stepDepth, int stepHeight, boolean upward)
            implements ShapeParams {
        @Override
        public ShapeType type() {
            return ShapeType.CORRIDOR;
        }
    }

    record Custom(ShapeMask mask) implements ShapeParams {
        public Custom {
            Objects.requireNonNull(mask, "mask");
        }

        @Override
        public ShapeType type() {
            return ShapeType.CUSTOM;
        }
    }
}
