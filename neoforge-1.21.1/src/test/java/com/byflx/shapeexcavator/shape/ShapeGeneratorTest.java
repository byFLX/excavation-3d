package com.byflx.shapeexcavator.shape;

import static com.byflx.shapeexcavator.shape.TestShapes.cells;
import static com.byflx.shapeexcavator.shape.TestShapes.codes;
import static com.byflx.shapeexcavator.shape.TestShapes.issues;
import static com.byflx.shapeexcavator.shape.TestShapes.valid;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.byflx.shapeexcavator.shape.ShapeIssue.Code;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

class ShapeGeneratorTest {
    // --- 1. Cuboid and templates ---

    @Test
    void cuboid3x3x1Selects9() {
        GeneratedShape shape = valid(new ShapeParams.Cuboid(3, 3, 1));
        assertEquals(9, shape.count());
        assertEquals(List.of(3, 3, 1), List.of(shape.width(), shape.height(), shape.depth()));
    }

    @Test
    void cuboid7x5x2Selects70() {
        assertEquals(70, valid(new ShapeParams.Cuboid(7, 5, 2)).count());
    }

    @Test
    void cuboidAtSelectionLimitIsValid() {
        assertEquals(4096, valid(new ShapeParams.Cuboid(32, 32, 4)).count());
    }

    @Test
    void cuboidAboveSelectionLimitIsRejected() {
        List<ShapeIssue> issues = issues(new ShapeParams.Cuboid(32, 32, 5));
        assertEquals(List.of(ShapeIssue.of(Code.SELECTION_TOO_LARGE, "selection", 5120, 4096)), issues);
    }

    @Test
    void cuboidAboveAxisLimitIsRejected() {
        List<ShapeIssue> issues = issues(new ShapeParams.Cuboid(33, 1, 1));
        assertEquals(List.of(ShapeIssue.of(Code.AXIS_TOO_LARGE, "width", 33, 32)), issues);
    }

    @Test
    void zeroAndNegativeDimensionsAreRejected() {
        List<ShapeIssue> issues = issues(new ShapeParams.Cuboid(0, -3, 1));
        assertEquals(List.of(
                ShapeIssue.of(Code.VALUE_TOO_SMALL, "width", 0, 1),
                ShapeIssue.of(Code.VALUE_TOO_SMALL, "height", -3, 1)), issues);
    }

    @Test
    void surfaceAndLineAreCuboidTemplates() {
        assertEquals(new ShapeParams.Cuboid(4, 3, 1), ShapeTemplates.surface(4, 3));
        assertEquals(new ShapeParams.Cuboid(5, 1, 1), ShapeTemplates.line(5, ShapeTemplates.LineAxis.WIDTH));
        assertEquals(new ShapeParams.Cuboid(1, 5, 1), ShapeTemplates.line(5, ShapeTemplates.LineAxis.HEIGHT));
        assertEquals(new ShapeParams.Cuboid(1, 1, 5), ShapeTemplates.line(5, ShapeTemplates.LineAxis.DEPTH));
        assertEquals(5, valid(ShapeTemplates.line(5, ShapeTemplates.LineAxis.DEPTH)).count());
    }

    @Test
    void parametricDefaultsAreValid() {
        for (ShapeType type : ShapeType.values()) {
            ShapeParams params = ShapeTemplates.defaults(type);
            assertEquals(type, params.type());
            if (type == ShapeType.CUSTOM) {
                assertEquals(Set.of(Code.EMPTY_MASK), codes(issues(params)));
            } else {
                assertTrue(valid(params).count() > 0, type.name());
            }
        }
    }

    // --- 2. Plus ---

    @Test
    void plus3x3x1Arm1IsExactlyFiveCells() {
        GeneratedShape shape = valid(new ShapeParams.Plus(3, 3, 1, 1));
        assertEquals(5, shape.count());
        assertEquals(cells(new int[] {1, 0, 0}, new int[] {0, 1, 0}, new int[] {1, 1, 0},
                new int[] {2, 1, 0}, new int[] {1, 2, 0}), new HashSet<>(shape.positions()));
    }

    @Test
    void plus5x5Counts() {
        assertEquals(9, valid(new ShapeParams.Plus(5, 5, 1, 1)).count());
        assertEquals(18, valid(new ShapeParams.Plus(5, 5, 2, 1)).count());
    }

    @Test
    void plusEvenSizePutsExtraCellOnHigherSide() {
        GeneratedShape shape = valid(new ShapeParams.Plus(4, 4, 1, 1));
        Set<Cell> expected = new HashSet<>();
        for (int i = 0; i < 4; i++) {
            expected.add(new Cell(1, i, 0));
            expected.add(new Cell(i, 1, 0));
        }
        assertEquals(7, shape.count());
        assertEquals(expected, new HashSet<>(shape.positions()));
    }

    @Test
    void plusArmThickerThanCrossSectionIsRejected() {
        assertEquals(List.of(ShapeIssue.of(Code.ARM_TOO_THICK, "arm", 3, 2)),
                issues(new ShapeParams.Plus(3, 2, 1, 3)));
        assertEquals(Set.of(Code.VALUE_TOO_SMALL), codes(issues(new ShapeParams.Plus(3, 3, 1, 0))));
    }

    // --- 3. Stair ---

    @Test
    void stairColumnHeights() {
        GeneratedShape up = valid(new ShapeParams.Stair(1, 3, 1, 1, true));
        GeneratedShape down = valid(new ShapeParams.Stair(1, 3, 1, 1, false));
        assertEquals(6, up.count());
        assertEquals(6, down.count());
        assertEquals(List.of(1, 2, 3), columnHeights(up.mask()));
        assertEquals(List.of(3, 2, 1), columnHeights(down.mask()));
    }

    @Test
    void stairSweepCountsAndExtents() {
        for (int w = 1; w <= 3; w++) {
            for (int n = 1; n <= 5; n++) {
                for (int l = 1; l <= 3; l++) {
                    for (int r = 1; r <= 3; r++) {
                        for (boolean ascending : new boolean[] {true, false}) {
                            GeneratedShape shape = valid(new ShapeParams.Stair(w, n, l, r, ascending));
                            String label = "W" + w + " N" + n + " L" + l + " R" + r + " asc=" + ascending;
                            assertEquals(w * l * r * n * (n + 1) / 2, shape.count(), label);
                            assertEquals(List.of(w, n * r, n * l),
                                    List.of(shape.width(), shape.height(), shape.depth()), label);
                        }
                    }
                }
            }
        }
    }

    // --- 4. Corridor ---

    @Test
    void corridorExampleFromPlan() {
        GeneratedShape shape = valid(new ShapeParams.Corridor(3, 3, 6, 1, 1, true));
        assertEquals(List.of(3, 8, 6), List.of(shape.width(), shape.height(), shape.depth()));
        assertEquals(54, shape.count());
        for (int z = 1; z < 6; z++) {
            assertFalse(shape.mask().contains(0, z - 1, z), "floor under step " + z + " must stay");
            assertTrue(shape.mask().contains(0, z, z));
            assertTrue(shape.mask().contains(0, z + 2, z));
            assertFalse(shape.mask().contains(0, z + 3, z));
        }
    }

    @Test
    void corridorDownIsVerticalMirrorOfUp() {
        for (int c = 1; c <= 4; c++) {
            for (int n = 1; n <= 4; n++) {
                for (int l = 1; l <= 2; l++) {
                    for (int r = 1; r <= 3; r++) {
                        ShapeMask up = valid(new ShapeParams.Corridor(2, c, n, l, r, true)).mask();
                        ShapeMask down = valid(new ShapeParams.Corridor(2, c, n, l, r, false)).mask();
                        Set<Cell> mirrored = new HashSet<>();
                        for (Cell cell : up.positions()) {
                            mirrored.add(new Cell(cell.x(), up.height() - 1 - cell.y(), cell.z()));
                        }
                        assertEquals(mirrored, new HashSet<>(down.positions()), "C" + c + " N" + n + " L" + l + " R" + r);
                    }
                }
            }
        }
    }

    @Test
    void corridorAndStairHaveDifferentMasks() {
        ShapeMask stair = valid(new ShapeParams.Stair(3, 6, 1, 1, true)).mask();
        ShapeMask corridor = valid(new ShapeParams.Corridor(3, 3, 6, 1, 1, true)).mask();
        assertNotEquals(new HashSet<>(stair.positions()), new HashSet<>(corridor.positions()));
        assertNotEquals(stair, corridor);
    }

    // --- 5. Derived oversize and hostile values ---

    @Test
    void derivedOversizeIsRejected() {
        assertEquals(List.of(ShapeIssue.of(Code.AXIS_TOO_LARGE, "height", 33, 32)),
                issues(new ShapeParams.Corridor(1, 32, 2, 1, 1, true)));
        assertEquals(List.of(ShapeIssue.of(Code.AXIS_TOO_LARGE, "height", 64, 32)),
                issues(new ShapeParams.Stair(1, 32, 1, 2, true)));
    }

    @Test
    void extremeValuesProduceIssuesNotExceptions() {
        int[] hostile = {Integer.MAX_VALUE, Integer.MIN_VALUE, 0, -1};
        for (int v : hostile) {
            List<ShapeParams> params = new ArrayList<>(List.of(
                    new ShapeParams.Cuboid(v, 1, 1), new ShapeParams.Cuboid(1, v, 1), new ShapeParams.Cuboid(1, 1, v),
                    new ShapeParams.Plus(v, 1, 1, 1), new ShapeParams.Plus(1, v, 1, 1),
                    new ShapeParams.Plus(1, 1, v, 1), new ShapeParams.Plus(1, 1, 1, v),
                    new ShapeParams.Stair(v, 1, 1, 1, true), new ShapeParams.Stair(1, v, 1, 1, true),
                    new ShapeParams.Stair(1, 1, v, 1, true), new ShapeParams.Stair(1, 1, 1, v, false),
                    new ShapeParams.Stair(v, v, v, v, true),
                    new ShapeParams.Corridor(v, 1, 1, 1, 1, true), new ShapeParams.Corridor(1, v, 1, 1, 1, true),
                    new ShapeParams.Corridor(1, 1, v, 1, 1, true), new ShapeParams.Corridor(1, 1, 1, v, 1, false),
                    new ShapeParams.Corridor(1, 1, 2, 1, v, false), new ShapeParams.Corridor(v, v, v, v, v, true)));
            for (ShapeParams p : params) {
                assertFalse(issues(p).isEmpty(), p.toString());
            }
        }
    }

    @Test
    void unsupportedFormatVersionIsAnIssue() {
        ShapeConfig config = new ShapeConfig(2, new ShapeParams.Cuboid(1, 1, 1), ShapeTransform.IDENTITY, 0L);
        assertEquals(List.of(ShapeIssue.of(Code.UNSUPPORTED_FORMAT_VERSION, "formatVersion", 2, 1)),
                issues(config, ShapeLimits.DEFAULT));
    }

    // --- 10. Order and determinism ---

    @Test
    void positionsAndOffsetsAreOrderedByZThenYThenX() {
        Comparator<Cell> order = Comparator.comparingInt(Cell::z).thenComparingInt(Cell::y).thenComparingInt(Cell::x);
        List<ShapeConfig> configs = List.of(
                ShapeConfig.of(new ShapeParams.Plus(5, 4, 3, 2), new ShapeTransform(1, true)),
                ShapeConfig.of(new ShapeParams.Stair(2, 3, 2, 1, false), new ShapeTransform(3, false)),
                ShapeConfig.of(new ShapeParams.Corridor(3, 3, 6, 1, 1, true)));
        Orientation orientation = new Orientation(Direction.EAST, Direction.UP, Direction.NORTH);
        for (ShapeConfig config : configs) {
            GeneratedShape shape = valid(config, ShapeLimits.DEFAULT);
            List<Cell> sorted = new ArrayList<>(shape.positions());
            sorted.sort(order);
            assertEquals(sorted, shape.positions());

            Cell a = shape.anchor();
            List<Offset> expected = new ArrayList<>();
            for (Cell c : sorted) {
                expected.add(orientation.toWorld(c.x() - a.x(), c.y() - a.y(), c.z() - a.z()));
            }
            assertEquals(expected, shape.worldOffsets(orientation));
        }
    }

    @Test
    void sameConfigGivesEqualResults() {
        ShapeConfig config = ShapeConfig.of(new ShapeParams.Plus(6, 5, 2, 2), new ShapeTransform(2, true));
        assertEquals(ShapeGenerator.generate(config, ShapeLimits.DEFAULT), ShapeGenerator.generate(config, ShapeLimits.DEFAULT));
        Orientation orientation = Orientation.orient(Direction.UP, Direction.WEST, Orientation.Mode.HIT_FACE);
        assertEquals(valid(config, ShapeLimits.DEFAULT).worldOffsets(orientation),
                valid(config, ShapeLimits.DEFAULT).worldOffsets(orientation));
    }

    private static List<Integer> columnHeights(ShapeMask mask) {
        List<Integer> heights = new ArrayList<>();
        for (int z = 0; z < mask.depth(); z++) {
            int height = 0;
            while (height < mask.height() && mask.contains(0, height, z)) {
                height++;
            }
            for (int y = height; y < mask.height(); y++) {
                assertFalse(mask.contains(0, y, z), "column must be contiguous from 0");
            }
            heights.add(height);
        }
        return heights;
    }
}
