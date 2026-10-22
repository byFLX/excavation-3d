package com.byflx.shapeexcavator.shape;

import com.byflx.shapeexcavator.shape.ShapeIssue.Code;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Pre-allocation checks (plan §4): raw parameters ≥ 1, overflow-safe derived extents, and every
 * extent within the axis limit. The selection count is checked by {@link ShapeGenerator} after
 * generating. User input never causes an exception here; problems are returned as data.
 */
public final class ShapeValidator {
    private ShapeValidator() {
    }

    public static List<ShapeIssue> validate(ShapeConfig config, ShapeLimits limits) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(limits, "limits");
        List<ShapeIssue> issues = new ArrayList<>();
        if (config.formatVersion() != ShapeConfig.FORMAT_VERSION) {
            issues.add(ShapeIssue.of(Code.UNSUPPORTED_FORMAT_VERSION, "formatVersion",
                    config.formatVersion(), ShapeConfig.FORMAT_VERSION));
            return List.copyOf(issues);
        }
        switch (config.params()) {
            case ShapeParams.Cuboid cuboid -> validateCuboid(cuboid, limits, issues);
            case ShapeParams.Plus plus -> validatePlus(plus, limits, issues);
            case ShapeParams.Stair stair -> validateStair(stair, limits, issues);
            case ShapeParams.Corridor corridor -> validateCorridor(corridor, limits, issues);
            case ShapeParams.Custom custom -> validateCustom(custom, limits, issues);
        }
        return List.copyOf(issues);
    }

    private static void validateCuboid(ShapeParams.Cuboid cuboid, ShapeLimits limits, List<ShapeIssue> issues) {
        requirePositive(issues, "width", cuboid.width());
        requirePositive(issues, "height", cuboid.height());
        requirePositive(issues, "depth", cuboid.depth());
        if (issues.isEmpty()) {
            checkExtents(issues, limits, cuboid.width(), cuboid.height(), cuboid.depth());
        }
    }

    private static void validatePlus(ShapeParams.Plus plus, ShapeLimits limits, List<ShapeIssue> issues) {
        requirePositive(issues, "width", plus.width());
        requirePositive(issues, "height", plus.height());
        requirePositive(issues, "depth", plus.depth());
        requirePositive(issues, "arm", plus.arm());
        if (!issues.isEmpty()) {
            return;
        }
        int maxArm = Math.min(plus.width(), plus.height());
        if (plus.arm() > maxArm) {
            issues.add(ShapeIssue.of(Code.ARM_TOO_THICK, "arm", plus.arm(), maxArm));
        }
        checkExtents(issues, limits, plus.width(), plus.height(), plus.depth());
    }

    private static void validateStair(ShapeParams.Stair stair, ShapeLimits limits, List<ShapeIssue> issues) {
        requirePositive(issues, "width", stair.width());
        requirePositive(issues, "steps", stair.steps());
        requirePositive(issues, "stepDepth", stair.stepDepth());
        requirePositive(issues, "stepHeight", stair.stepHeight());
        if (!issues.isEmpty()) {
            return;
        }
        long height;
        long depth;
        try {
            height = Math.multiplyExact((long) stair.steps(), (long) stair.stepHeight());
            depth = Math.multiplyExact((long) stair.steps(), (long) stair.stepDepth());
        } catch (ArithmeticException e) {
            issues.add(ShapeIssue.of(Code.SIZE_OVERFLOW, "steps"));
            return;
        }
        checkExtents(issues, limits, stair.width(), height, depth);
    }

    private static void validateCorridor(ShapeParams.Corridor corridor, ShapeLimits limits, List<ShapeIssue> issues) {
        requirePositive(issues, "width", corridor.width());
        requirePositive(issues, "clearance", corridor.clearance());
        requirePositive(issues, "steps", corridor.steps());
        requirePositive(issues, "stepDepth", corridor.stepDepth());
        requirePositive(issues, "stepHeight", corridor.stepHeight());
        if (!issues.isEmpty()) {
            return;
        }
        long height;
        long depth;
        try {
            long rise = Math.multiplyExact((long) corridor.steps() - 1, (long) corridor.stepHeight());
            height = Math.addExact((long) corridor.clearance(), rise);
            depth = Math.multiplyExact((long) corridor.steps(), (long) corridor.stepDepth());
        } catch (ArithmeticException e) {
            issues.add(ShapeIssue.of(Code.SIZE_OVERFLOW, "steps"));
            return;
        }
        checkExtents(issues, limits, corridor.width(), height, depth);
    }

    private static void validateCustom(ShapeParams.Custom custom, ShapeLimits limits, List<ShapeIssue> issues) {
        ShapeMask mask = custom.mask();
        checkExtents(issues, limits, mask.width(), mask.height(), mask.depth());
        if (mask.isEmpty()) {
            issues.add(ShapeIssue.of(Code.EMPTY_MASK, "mask"));
            return;
        }
        Cell anchor = mask.anchor();
        if (!mask.contains(anchor)) {
            issues.add(ShapeIssue.of(Code.ANCHOR_NOT_SELECTED, "anchor", anchor.x(), anchor.y(), anchor.z()));
        }
        int front = mask.frontLayer().getAsInt();
        if (anchor.z() != front) {
            issues.add(ShapeIssue.of(Code.ANCHOR_NOT_IN_FRONT_LAYER, "anchor", anchor.z(), front));
        }
    }

    private static void requirePositive(List<ShapeIssue> issues, String field, int value) {
        if (value < 1) {
            issues.add(ShapeIssue.of(Code.VALUE_TOO_SMALL, field, value, 1));
        }
    }

    private static void checkExtents(List<ShapeIssue> issues, ShapeLimits limits, long width, long height, long depth) {
        checkExtent(issues, limits, "width", width);
        checkExtent(issues, limits, "height", height);
        checkExtent(issues, limits, "depth", depth);
    }

    private static void checkExtent(List<ShapeIssue> issues, ShapeLimits limits, String field, long extent) {
        if (extent > limits.maxAxis()) {
            issues.add(ShapeIssue.of(Code.AXIS_TOO_LARGE, field, extent, limits.maxAxis()));
        }
    }
}
