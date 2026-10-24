package com.byflx.excavation3d.preview;

import com.byflx.excavation3d.shape.Cell;
import com.byflx.excavation3d.shape.ShapeMask;
import com.byflx.excavation3d.shape.ShapeTransform;
import com.byflx.excavation3d.shape.ShapeLimits;
import java.util.BitSet;
import java.util.Optional;
import java.util.ArrayList;
import java.util.List;

/** Custom canvas remains visible even when empty or invalid for Apply. */
public final class CustomCanvas {
    private CustomCanvas() { }

    public record Growth(ShapeMask mask, Cell target) { }

    /** Grow only the required sides by one cell; translate the selection and start together. */
    public static Optional<Growth> growToInclude(ShapeMask mask, Cell target, ShapeLimits limits) {
        if (target.x() < -1 || target.x() > mask.width() || target.y() < -1 || target.y() > mask.height()
                || target.z() < -1 || target.z() > mask.depth()) return Optional.empty();
        int dx = target.x() < 0 ? 1 : 0, dy = target.y() < 0 ? 1 : 0, dz = target.z() < 0 ? 1 : 0;
        int w = mask.width() + dx + (target.x() >= mask.width() ? 1 : 0);
        int h = mask.height() + dy + (target.y() >= mask.height() ? 1 : 0);
        int d = mask.depth() + dz + (target.z() >= mask.depth() ? 1 : 0);
        if (w > limits.maxAxis() || h > limits.maxAxis() || d > limits.maxAxis()) return Optional.empty();
        BitSet bits = new BitSet(w * h * d);
        for (Cell cell : mask.positions()) bits.set(((cell.z() + dz) * h + cell.y() + dy) * w + cell.x() + dx);
        Cell anchor = mask.anchor();
        return Optional.of(new Growth(ShapeMask.of(w, h, d, bits,
                new Cell(anchor.x() + dx, anchor.y() + dy, anchor.z() + dz)),
                new Cell(target.x() + dx, target.y() + dy, target.z() + dz)));
    }

    /** Empty cells of the current depth layer; all axes are also shown by the full canvas outline. */
    public static List<Cell> ghosts(ShapeMask mask, int layer) {
        List<Cell> cells = new ArrayList<>();
        if (layer >= 0 && layer < mask.depth()) {
            for (int y = 0; y < mask.height(); y++) {
                for (int x = 0; x < mask.width(); x++) {
                    if (!mask.contains(x, y, layer)) cells.add(new Cell(x, y, layer));
                }
            }
        }
        return List.copyOf(cells);
    }

    /** Maps a displayed cell back into the editable, untransformed grid. */
    public static Cell toDraft(Cell cell, int width, int height, ShapeTransform transform) {
        int x = cell.x(), y = cell.y();
        int w = transform.rotation() % 2 == 0 ? width : height;
        int h = transform.rotation() % 2 == 0 ? height : width;
        for (int turn = 0; turn < transform.rotation(); turn++) {
            int oldX = x;
            x = h - 1 - y;
            y = oldX;
            int oldW = w; w = h; h = oldW;
        }
        if (transform.mirrored()) x = width - 1 - x;
        return new Cell(x, y, cell.z());
    }
}
