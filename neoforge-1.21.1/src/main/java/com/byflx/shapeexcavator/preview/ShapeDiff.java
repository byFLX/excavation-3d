package com.byflx.shapeexcavator.preview;

import com.byflx.shapeexcavator.shape.Cell;
import com.byflx.shapeexcavator.shape.GeneratedShape;
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

    public boolean isEmpty() {
        return added.isEmpty() && removed.isEmpty();
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
