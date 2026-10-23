package com.byflx.excavation3d.preview;

import com.byflx.excavation3d.shape.Cell;
import com.byflx.excavation3d.shape.ShapeMask;
import com.byflx.excavation3d.shape.ShapeTransform;
import java.util.ArrayList;
import java.util.List;

/** Custom canvas remains visible even when empty or invalid for Apply. */
public final class CustomCanvas {
    private CustomCanvas() { }

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
