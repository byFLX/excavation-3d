package com.byflx.shapeexcavator.shape;

import java.util.Objects;
import net.minecraft.core.Direction;

/**
 * World directions of the local shape axes: width → {@code right}, height → {@code up},
 * depth → {@code forward} (into the block). Plan §7.
 */
public record Orientation(Direction right, Direction up, Direction forward) {
    /** How the local axes are derived from the hit. */
    public enum Mode {
        /** Normal shapes: the hit face sets the depth direction. */
        HIT_FACE,
        /** Stairs and corridors: world up and the player's horizontal facing; the hit face is ignored. */
        PLAYER_HORIZONTAL;

        public static Mode forType(ShapeType type) {
            return switch (type) {
                case STAIR, CORRIDOR -> PLAYER_HORIZONTAL;
                case CUBOID, PLUS, CUSTOM -> HIT_FACE;
            };
        }
    }

    public Orientation {
        Objects.requireNonNull(right, "right");
        Objects.requireNonNull(up, "up");
        Objects.requireNonNull(forward, "forward");
        if (dot(right, up) != 0 || dot(right, forward) != 0 || dot(up, forward) != 0) {
            throw new IllegalArgumentException("axes must be pairwise perpendicular: " + right + ", " + up + ", " + forward);
        }
    }

    /**
     * @param hitFace        the clicked block face
     * @param playerFacing   the player's horizontal facing; must not be UP or DOWN
     */
    public static Orientation orient(Direction hitFace, Direction playerFacing, Mode mode) {
        Objects.requireNonNull(hitFace, "hitFace");
        Objects.requireNonNull(playerFacing, "playerFacing");
        Objects.requireNonNull(mode, "mode");
        if (playerFacing.getAxis() == Direction.Axis.Y) {
            throw new IllegalArgumentException("playerFacing must be horizontal: " + playerFacing);
        }
        if (mode == Mode.PLAYER_HORIZONTAL) {
            return new Orientation(playerFacing.getClockWise(), Direction.UP, playerFacing);
        }
        Direction forward = hitFace.getOpposite();
        if (forward.getAxis() == Direction.Axis.Y) {
            return new Orientation(playerFacing.getClockWise(), playerFacing, forward);
        }
        return new Orientation(forward.getClockWise(), Direction.UP, forward);
    }

    /** Maps a local offset (relative to the anchor) to a world offset. */
    public Offset toWorld(int localX, int localY, int localZ) {
        return new Offset(
                localX * right.getStepX() + localY * up.getStepX() + localZ * forward.getStepX(),
                localX * right.getStepY() + localY * up.getStepY() + localZ * forward.getStepY(),
                localX * right.getStepZ() + localY * up.getStepZ() + localZ * forward.getStepZ());
    }

    private static int dot(Direction a, Direction b) {
        return a.getStepX() * b.getStepX() + a.getStepY() * b.getStepY() + a.getStepZ() * b.getStepZ();
    }
}
