package com.byflx.excavation3d.shape;

import com.byflx.excavation3d.shape.ShapeIssue.Code;
import java.util.BitSet;
import java.util.List;
import java.util.Objects;

/**
 * The only shape algorithm (plan §9): editor, world preview and server all call
 * {@link #generate(ShapeConfig, ShapeLimits)}.
 */
public final class ShapeGenerator {
    private ShapeGenerator() {
    }

    public sealed interface Result permits Valid, Invalid {
        default boolean isValid() {
            return this instanceof Valid;
        }
    }

    public record Valid(GeneratedShape shape) implements Result {
        public Valid {
            Objects.requireNonNull(shape, "shape");
        }
    }

    public record Invalid(List<ShapeIssue> issues) implements Result {
        public Invalid {
            issues = List.copyOf(issues);
            if (issues.isEmpty()) {
                throw new IllegalArgumentException("an invalid result needs at least one issue");
            }
        }
    }

    public static Result generate(ShapeConfig config, ShapeLimits limits) {
        List<ShapeIssue> issues = ShapeValidator.validate(config, limits);
        if (!issues.isEmpty()) {
            return new Invalid(issues);
        }
        // Validation guarantees every extent is within limits.maxAxis() ≤ 32 before anything is allocated.
        ShapeMask local = buildLocal(config.params());
        if (local.count() > limits.maxSelected()) {
            return new Invalid(List.of(
                    ShapeIssue.of(Code.SELECTION_TOO_LARGE, "selection", local.count(), limits.maxSelected())));
        }
        return new Valid(new GeneratedShape(config.transform().apply(local)));
    }

    private static ShapeMask buildLocal(ShapeParams params) {
        return switch (params) {
            case ShapeParams.Cuboid cuboid -> buildCuboid(cuboid);
            case ShapeParams.Plus plus -> buildPlus(plus);
            case ShapeParams.Stair stair -> buildStair(stair);
            case ShapeParams.Corridor corridor -> buildCorridor(corridor);
            case ShapeParams.Custom custom -> custom.mask();
        };
    }

    private static ShapeMask buildCuboid(ShapeParams.Cuboid cuboid) {
        int w = cuboid.width();
        int h = cuboid.height();
        return ShapeMask.full(w, h, cuboid.depth(), new Cell(centre(w), centre(h), 0));
    }

    private static ShapeMask buildPlus(ShapeParams.Plus plus) {
        int w = plus.width();
        int h = plus.height();
        int arm = plus.arm();
        // With even leftovers the extra cell lands on the higher-index side.
        int barX = (w - arm) / 2;
        int barY = (h - arm) / 2;
        BitSet bits = new BitSet(w * h * plus.depth());
        for (int z = 0; z < plus.depth(); z++) {
            for (int y = 0; y < h; y++) {
                boolean inHorizontalBar = y >= barY && y < barY + arm;
                for (int x = 0; x < w; x++) {
                    boolean inVerticalBar = x >= barX && x < barX + arm;
                    if (inVerticalBar || inHorizontalBar) {
                        bits.set(ShapeMask.index(w, h, x, y, z));
                    }
                }
            }
        }
        return ShapeMask.wrap(w, h, plus.depth(), bits, new Cell(centre(w), centre(h), 0));
    }

    private static ShapeMask buildStair(ShapeParams.Stair stair) {
        int w = stair.width();
        int n = stair.steps();
        int h = n * stair.stepHeight();
        int d = n * stair.stepDepth();
        BitSet bits = new BitSet(w * h * d);
        for (int z = 0; z < d; z++) {
            int step = z / stair.stepDepth();
            if (!stair.ascending()) {
                step = n - 1 - step;
            }
            int top = stair.stepHeight() * (step + 1);
            for (int y = 0; y < top; y++) {
                for (int x = 0; x < w; x++) {
                    bits.set(ShapeMask.index(w, h, x, y, z));
                }
            }
        }
        return ShapeMask.wrap(w, h, d, bits, new Cell(centre(w), 0, 0));
    }

    private static ShapeMask buildCorridor(ShapeParams.Corridor corridor) {
        int w = corridor.width();
        int n = corridor.steps();
        int rise = (n - 1) * corridor.stepHeight();
        int h = corridor.clearance() + rise;
        int d = n * corridor.stepDepth();
        BitSet bits = new BitSet(w * h * d);
        for (int z = 0; z < d; z++) {
            int step = z / corridor.stepDepth();
            // Normalised into the box: "down" starts at the top (y = rise) and descends to 0.
            int floor = corridor.upward() ? step * corridor.stepHeight() : rise - step * corridor.stepHeight();
            for (int y = floor; y < floor + corridor.clearance(); y++) {
                for (int x = 0; x < w; x++) {
                    bits.set(ShapeMask.index(w, h, x, y, z));
                }
            }
        }
        int anchorY = corridor.upward() ? 0 : rise;
        return ShapeMask.wrap(w, h, d, bits, new Cell(centre(w), anchorY, 0));
    }

    /** floor((size − 1) / 2) for size ≥ 1. */
    private static int centre(int size) {
        return (size - 1) / 2;
    }
}
