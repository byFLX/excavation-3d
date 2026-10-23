package com.byflx.excavation3d.preview;

import com.byflx.excavation3d.shape.Cell;
import com.byflx.excavation3d.shape.GeneratedShape;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Cells added and removed by a size change, relative to the anchor (cell minus anchor), because
 * the anchor is the block that stays in place in the world.
 */
public record ShapeDiff(Set<Cell> added, Set<Cell> removed) {
    public static final ShapeDiff NONE = new ShapeDiff(Set.of(), Set.of());

    public ShapeDiff {
        added = Set.copyOf(added);
        removed = Set.copyOf(removed);
    }

    public static ShapeDiff between(GeneratedShape before, GeneratedShape after) {
        Objects.requireNonNull(before, "before");
        Objects.requireNonNull(after, "after");
        Set<Cell> old = relative(before);
        Set<Cell> now = relative(after);
        Set<Cell> added = new HashSet<>(now);
        added.removeAll(old);
        Set<Cell> removed = new HashSet<>(old);
        removed.removeAll(now);
        return new ShapeDiff(added, removed);
    }

    /** An axis-aligned box in the shown shape's cell coordinates, max exclusive. */
    public record Bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    }

    public boolean isEmpty() {
        return added.isEmpty() && removed.isEmpty();
    }

    /**
     * The box the preview draws while this diff is highlighted on {@code shown}: the shape's own
     * bounding box plus the removed cells, which stay visible as ghosts outside it. Labels must keep
     * clear of all of it.
     */
    public Bounds bounds(GeneratedShape shown) {
        Cell anchor = shown.anchor();
        int minX = 0;
        int minY = 0;
        int minZ = 0;
        int maxX = shown.width();
        int maxY = shown.height();
        int maxZ = shown.depth();
        for (Cell rel : removed) {
            int x = rel.x() + anchor.x();
            int y = rel.y() + anchor.y();
            int z = rel.z() + anchor.z();
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x + 1);
            maxY = Math.max(maxY, y + 1);
            maxZ = Math.max(maxZ, z + 1);
        }
        return new Bounds(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static Set<Cell> relative(GeneratedShape shape) {
        Cell anchor = shape.anchor();
        Set<Cell> cells = new HashSet<>(shape.count());
        for (Cell cell : shape.positions()) {
            cells.add(new Cell(cell.x() - anchor.x(), cell.y() - anchor.y(), cell.z() - anchor.z()));
        }
        return cells;
    }
}
