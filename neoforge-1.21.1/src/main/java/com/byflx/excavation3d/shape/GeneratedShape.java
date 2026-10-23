package com.byflx.excavation3d.shape;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** A valid, transformed shape: the single geometric selection shared by editor, world preview and server. */
public record GeneratedShape(ShapeMask mask) {
    public GeneratedShape {
        Objects.requireNonNull(mask, "mask");
    }

    public int width() {
        return mask.width();
    }

    public int height() {
        return mask.height();
    }

    public int depth() {
        return mask.depth();
    }

    public int count() {
        return mask.count();
    }

    public Cell anchor() {
        return mask.anchor();
    }

    /** Selected local cells after the transform, ordered by z, then y, then x ascending. */
    public List<Cell> positions() {
        return mask.positions();
    }

    /**
     * World offsets relative to the anchor block, in {@link #positions()} order. The anchor itself
     * maps to {@link Offset#ZERO} and is included.
     */
    public List<Offset> worldOffsets(Orientation orientation) {
        Objects.requireNonNull(orientation, "orientation");
        Cell anchor = mask.anchor();
        List<Offset> offsets = new ArrayList<>(mask.count());
        for (Cell cell : mask.positions()) {
            offsets.add(orientation.toWorld(cell.x() - anchor.x(), cell.y() - anchor.y(), cell.z() - anchor.z()));
        }
        return Collections.unmodifiableList(offsets);
    }
}
