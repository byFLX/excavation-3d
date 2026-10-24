package com.byflx.excavation3d.shape;

import static com.byflx.excavation3d.shape.TestShapes.valid;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

class OrientationTest {
    private static final List<Direction> HORIZONTAL =
            List.of(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST);

    @Test
    void axesArePairwisePerpendicularForAllFacesAndFacings() {
        for (Orientation.Mode mode : Orientation.Mode.values()) {
            for (Direction face : Direction.values()) {
                for (Direction facing : HORIZONTAL) {
                    Orientation o = Orientation.orient(face, facing, mode);
                    String label = mode + " " + face + " " + facing;
                    assertEquals(0, dot(o.right(), o.up()), label);
                    assertEquals(0, dot(o.right(), o.forward()), label);
                    assertEquals(0, dot(o.up(), o.forward()), label);
                }
            }
        }
    }

    @Test
    void expectedValues() {
        assertEquals(new Orientation(Direction.EAST, Direction.UP, Direction.NORTH),
                Orientation.orient(Direction.SOUTH, Direction.NORTH, Orientation.Mode.HIT_FACE));
        assertEquals(new Orientation(Direction.EAST, Direction.NORTH, Direction.DOWN),
                Orientation.orient(Direction.UP, Direction.NORTH, Orientation.Mode.HIT_FACE));
        assertEquals(new Orientation(Direction.EAST, Direction.NORTH, Direction.UP),
                Orientation.orient(Direction.DOWN, Direction.NORTH, Orientation.Mode.HIT_FACE));
    }

    @Test
    void sideFacesSetDepthAndKeepWorldUp() {
        for (Direction face : HORIZONTAL) {
            for (Direction facing : HORIZONTAL) {
                Orientation o = Orientation.orient(face, facing, Orientation.Mode.HIT_FACE);
                assertEquals(face.getOpposite(), o.forward());
                assertEquals(Direction.UP, o.up());
                assertEquals(face.getOpposite().getClockWise(), o.right());
            }
        }
    }

    @Test
    void stairModeIgnoresHitFace() {
        for (Direction facing : HORIZONTAL) {
            Orientation expected = new Orientation(facing.getClockWise(), Direction.UP, facing);
            for (Direction face : Direction.values()) {
                assertEquals(expected, Orientation.orient(face, facing, Orientation.Mode.PLAYER_HORIZONTAL));
            }
        }
        assertEquals(Orientation.Mode.PLAYER_HORIZONTAL, Orientation.Mode.forType(ShapeType.STAIR));
        assertEquals(Orientation.Mode.PLAYER_HORIZONTAL, Orientation.Mode.forType(ShapeType.CORRIDOR));
        assertEquals(Orientation.Mode.HIT_FACE, Orientation.Mode.forType(ShapeType.PLUS));
    }

    @Test
    void worldOffsetsOnTopFaceRunHeightTowardsPlayerFacing() {
        GeneratedShape column = valid(new ShapeParams.Cuboid(1, 2, 1));
        Orientation o = Orientation.orient(Direction.UP, Direction.NORTH, Orientation.Mode.HIT_FACE);
        assertEquals(List.of(Offset.ZERO, new Offset(0, 0, -1)), column.worldOffsets(o));

        GeneratedShape deep = valid(new ShapeParams.Cuboid(1, 1, 3));
        assertEquals(List.of(Offset.ZERO, new Offset(0, -1, 0), new Offset(0, -2, 0)), deep.worldOffsets(o));
    }

    @Test
    void verticalPlayerFacingAndSkewAxesAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> Orientation.orient(Direction.NORTH, Direction.UP, Orientation.Mode.HIT_FACE));
        assertThrows(IllegalArgumentException.class,
                () -> new Orientation(Direction.EAST, Direction.EAST, Direction.NORTH));
    }

    private static int dot(Direction a, Direction b) {
        return a.getStepX() * b.getStepX() + a.getStepY() * b.getStepY() + a.getStepZ() * b.getStepZ();
    }
}
