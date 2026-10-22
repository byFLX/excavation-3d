package com.byflx.shapeexcavator.shape;

import static org.junit.jupiter.api.Assertions.fail;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

final class TestShapes {
    private TestShapes() {
    }

    static GeneratedShape valid(ShapeParams params) {
        return valid(ShapeConfig.of(params), ShapeLimits.DEFAULT);
    }

    static GeneratedShape valid(ShapeConfig config, ShapeLimits limits) {
        ShapeGenerator.Result result = ShapeGenerator.generate(config, limits);
        if (result instanceof ShapeGenerator.Valid valid) {
            return valid.shape();
        }
        return fail("expected a valid shape for " + config + " but got " + result);
    }

    static List<ShapeIssue> issues(ShapeParams params) {
        return issues(ShapeConfig.of(params), ShapeLimits.DEFAULT);
    }

    static List<ShapeIssue> issues(ShapeConfig config, ShapeLimits limits) {
        ShapeGenerator.Result result = ShapeGenerator.generate(config, limits);
        if (result instanceof ShapeGenerator.Invalid invalid) {
            return invalid.issues();
        }
        return fail("expected issues for " + config + " but got " + result);
    }

    static Set<ShapeIssue.Code> codes(List<ShapeIssue> issues) {
        return issues.stream().map(ShapeIssue::code).collect(Collectors.toSet());
    }

    /** Cells given as {x, y, z} triples. */
    static Set<Cell> cells(int[]... xyz) {
        Set<Cell> set = new HashSet<>();
        for (int[] c : xyz) {
            set.add(new Cell(c[0], c[1], c[2]));
        }
        return set;
    }

    /** Offsets of each selected cell relative to the anchor, in local coordinates. */
    static Set<Cell> localOffsets(ShapeMask mask) {
        Cell a = mask.anchor();
        return mask.positions().stream()
                .map(c -> new Cell(c.x() - a.x(), c.y() - a.y(), c.z() - a.z()))
                .collect(Collectors.toSet());
    }
}
