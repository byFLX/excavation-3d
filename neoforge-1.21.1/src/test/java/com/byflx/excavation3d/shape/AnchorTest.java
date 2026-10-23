package com.byflx.excavation3d.shape;

import static com.byflx.excavation3d.shape.TestShapes.valid;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

class AnchorTest {
    @Test
    void evenCuboidOffsetsFollowPlan() {
        GeneratedShape shape = valid(new ShapeParams.Cuboid(4, 2, 1));
        List<Offset> offsets = shape.worldOffsets(new Orientation(Direction.EAST, Direction.UP, Direction.NORTH));
        assertEquals(Set.of(-1, 0, 1, 2), offsets.stream().map(Offset::x).collect(Collectors.toSet()));
        assertEquals(Set.of(0, 1), offsets.stream().map(Offset::y).collect(Collectors.toSet()));
        assertEquals(Set.of(0), offsets.stream().map(Offset::z).collect(Collectors.toSet()));
    }

    @Test
    void defaultAnchors() {
        assertEquals(new Cell(1, 1, 0), valid(new ShapeParams.Cuboid(3, 4, 2)).anchor());
        assertEquals(new Cell(2, 0, 0), valid(new ShapeParams.Stair(5, 3, 1, 2, false)).anchor());
        assertEquals(new Cell(1, 0, 0), valid(new ShapeParams.Corridor(3, 3, 6, 1, 1, true)).anchor());
        assertEquals(new Cell(1, 5, 0), valid(new ShapeParams.Corridor(3, 3, 6, 1, 1, false)).anchor());
    }

    @Test
    void anchorIsSelectedForEveryTemplateAndTransform() {
        List<ShapeParams> params = new ArrayList<>();
        for (int w = 1; w <= 8; w++) {
            for (int h = 1; h <= 8; h++) {
                for (int d = 1; d <= 3; d++) {
                    params.add(new ShapeParams.Cuboid(w, h, d));
                }
                for (int arm = 1; arm <= Math.min(w, h); arm++) {
                    params.add(new ShapeParams.Plus(w, h, 2, arm));
                }
            }
        }
        for (int w = 1; w <= 3; w++) {
            for (int n = 1; n <= 8; n++) {
                for (int l = 1; l <= 3; l++) {
                    for (int r = 1; r <= 3; r++) {
                        for (boolean dir : new boolean[] {true, false}) {
                            params.add(new ShapeParams.Stair(w, n, l, r, dir));
                            params.add(new ShapeParams.Corridor(w, 1 + (n + r) % 4, n, l, r, dir));
                        }
                    }
                }
            }
        }
        boolean[][][] cells = new boolean[3][2][2];
        cells[2][1][0] = true;
        cells[0][0][1] = true;
        params.add(new ShapeParams.Custom(ShapeMask.of(cells, new Cell(2, 1, 0))));

        for (ShapeParams p : params) {
            for (ShapeTransform transform : ShapeTransform.all()) {
                GeneratedShape shape = valid(ShapeConfig.of(p, transform), ShapeLimits.DEFAULT);
                assertTrue(shape.mask().contains(shape.anchor()), p + " " + transform);
                assertTrue(shape.worldOffsets(new Orientation(Direction.EAST, Direction.UP, Direction.NORTH))
                        .contains(Offset.ZERO), p + " " + transform);
            }
        }
    }
}
