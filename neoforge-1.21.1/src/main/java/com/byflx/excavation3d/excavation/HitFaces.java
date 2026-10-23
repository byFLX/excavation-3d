package com.byflx.excavation3d.excavation;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Server-side choice of the start block's hit face (plan §7: the orientation is checked on the
 * server). The face in the mining packet comes from the client and is never trusted on its own.
 *
 * <ol>
 *   <li>The server's own ray from the player's eye, if it hits the start block.</li>
 *   <li>Otherwise (the server's view direction can lag a tick behind) the client's face, but only
 *       if the player's eye is in front of that face, i.e. the player could have targeted it.</li>
 *   <li>Otherwise the face the eye is furthest in front of.</li>
 * </ol>
 */
public final class HitFaces {
    private HitFaces() {
    }

    /**
     * @param claimed the face from the client's packet, or {@code null}
     * @param traced  the face of the server's ray hit on the start block, or {@code null} if it missed
     * @param box     the start block's outline in world coordinates
     * @param eye     the player's eye position as the server knows it
     */
    public static Direction choose(Direction claimed, Direction traced, AABB box, Vec3 eye) {
        if (traced != null && visibleFrom(traced, box, eye)) {
            return traced;
        }
        if (claimed != null && visibleFrom(claimed, box, eye)) {
            return claimed;
        }
        Direction best = null;
        double bestGap = 0;
        for (Direction face : Direction.values()) {
            double gap = gap(face, box, eye);
            if (gap > bestGap) {
                best = face;
                bestGap = gap;
            }
        }
        if (best != null) {
            return best;
        }
        // Eye inside the outline: no face is in front of it.
        Vec3 centre = box.getCenter();
        return Direction.getNearest((float) (eye.x - centre.x), (float) (eye.y - centre.y), (float) (eye.z - centre.z));
    }

    /** Whether the eye is strictly in front of the face's plane. */
    public static boolean visibleFrom(Direction face, AABB box, Vec3 eye) {
        return gap(face, box, eye) > 0;
    }

    private static double gap(Direction face, AABB box, Vec3 eye) {
        boolean positive = face.getAxisDirection() == Direction.AxisDirection.POSITIVE;
        return switch (face.getAxis()) {
            case X -> positive ? eye.x - box.maxX : box.minX - eye.x;
            case Y -> positive ? eye.y - box.maxY : box.minY - eye.y;
            case Z -> positive ? eye.z - box.maxZ : box.minZ - eye.z;
        };
    }
}
