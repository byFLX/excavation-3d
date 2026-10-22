package com.byflx.shapeexcavator.preview;

import com.byflx.shapeexcavator.shape.Offset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Which faces of the world preview get the light fill (request 2026-10-22): only the outer faces of
 * the blocks expected to be mined. Air, excluded and unloaded positions get nothing. The start
 * block is returned separately, because it is highlighted on its own.
 */
public final class PreviewFill {
    public enum Side {
        WEST(-1, 0, 0),
        EAST(1, 0, 0),
        DOWN(0, -1, 0),
        UP(0, 1, 0),
        NORTH(0, 0, -1),
        SOUTH(0, 0, 1);

        public final int dx;
        public final int dy;
        public final int dz;

        Side(int dx, int dy, int dz) {
            this.dx = dx;
            this.dy = dy;
            this.dz = dz;
        }
    }

    /** The face of cell (x, y, z) on {@code side}. */
    public record Face(int x, int y, int z, Side side) {
    }

    /**
     * @param faces fill faces of {@link CellCategory#PRESENT} cells whose neighbour is not mined
     * @param start the start cell, if the map contains one
     * @param count blocks expected to be mined: present plus start
     */
    public record Result(List<Face> faces, Optional<Offset> start, int count) {
    }

    private PreviewFill() {
    }

    public static Result of(Map<Offset, CellCategory> cells) {
        List<Face> faces = new ArrayList<>();
        Offset start = null;
        int count = 0;
        for (Map.Entry<Offset, CellCategory> entry : cells.entrySet()) {
            CellCategory category = entry.getValue();
            if (!category.mined()) {
                continue;
            }
            count++;
            Offset c = entry.getKey();
            if (category == CellCategory.START) {
                start = c;
                continue;
            }
            for (Side side : Side.values()) {
                CellCategory neighbour = cells.get(new Offset(c.x() + side.dx, c.y() + side.dy, c.z() + side.dz));
                if (neighbour == null || !neighbour.mined()) {
                    faces.add(new Face(c.x(), c.y(), c.z(), side));
                }
            }
        }
        return new Result(Collections.unmodifiableList(faces), Optional.ofNullable(start), count);
    }
}
