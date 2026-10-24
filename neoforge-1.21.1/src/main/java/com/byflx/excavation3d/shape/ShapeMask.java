package com.byflx.excavation3d.shape;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;

/**
 * Immutable W×H×D cell selection plus an anchor cell.
 *
 * <p>Bit layout of {@link #toBitSet()} and {@link #of(int, int, int, BitSet, Cell)}:
 * {@code index = (z * height + y) * width + x}. Inputs are copied and the internal bit set is
 * never exposed, so a mask cannot be mutated after construction (plan §9).
 *
 * <p>Structural invariants (axes 1..{@value ShapeLimits#TECHNICAL_MAX_AXIS}, anchor inside the box,
 * no cell outside the box) throw {@link IllegalArgumentException}. Semantic checks that depend on
 * user intent (empty mask, anchor selected, anchor in the front layer, lowered limits) are reported
 * as {@link ShapeIssue}s by {@link ShapeValidator}.
 */
public final class ShapeMask {
    private final int width;
    private final int height;
    private final int depth;
    private final Cell anchor;
    private final BitSet bits;
    private final List<Cell> positions;

    private ShapeMask(int width, int height, int depth, BitSet bits, Cell anchor) {
        checkAxes(width, height, depth);
        Objects.requireNonNull(anchor, "anchor");
        if (!inBox(width, height, depth, anchor.x(), anchor.y(), anchor.z())) {
            throw new IllegalArgumentException("anchor " + anchor + " outside " + width + "x" + height + "x" + depth);
        }
        checkCellsInBox(bits, width * height * depth);
        this.width = width;
        this.height = height;
        this.depth = depth;
        this.anchor = anchor;
        this.bits = bits;

        List<Cell> cells = new ArrayList<>(bits.cardinality());
        for (int i = bits.nextSetBit(0); i >= 0; i = bits.nextSetBit(i + 1)) {
            int rest = i / width;
            cells.add(new Cell(i % width, rest % height, rest / height));
        }
        this.positions = Collections.unmodifiableList(cells);
    }

    /**
     * Copies {@code cells} (layout: see class doc). Bounds are checked before copying and only the
     * box range is copied, so an oversized input never causes an oversized allocation.
     */
    public static ShapeMask of(int width, int height, int depth, BitSet cells, Cell anchor) {
        Objects.requireNonNull(cells, "cells");
        checkAxes(width, height, depth);
        int size = width * height * depth;
        checkCellsInBox(cells, size);
        return new ShapeMask(width, height, depth, cells.get(0, size), anchor);
    }

    /** Copies {@code cells}, indexed as {@code cells[x][y][z]}; the array must not be ragged. */
    public static ShapeMask of(boolean[][][] cells, Cell anchor) {
        Objects.requireNonNull(cells, "cells");
        if (cells.length == 0 || cells[0].length == 0 || cells[0][0].length == 0) {
            throw new IllegalArgumentException("cells must not have an empty axis");
        }
        int width = cells.length;
        int height = cells[0].length;
        int depth = cells[0][0].length;
        checkAxes(width, height, depth);
        BitSet bits = new BitSet(width * height * depth);
        for (int x = 0; x < width; x++) {
            if (cells[x].length != height) {
                throw new IllegalArgumentException("ragged cells array at x=" + x);
            }
            for (int y = 0; y < height; y++) {
                if (cells[x][y].length != depth) {
                    throw new IllegalArgumentException("ragged cells array at x=" + x + ", y=" + y);
                }
                for (int z = 0; z < depth; z++) {
                    if (cells[x][y][z]) {
                        bits.set(index(width, height, x, y, z));
                    }
                }
            }
        }
        return new ShapeMask(width, height, depth, bits, anchor);
    }

    public static ShapeMask empty(int width, int height, int depth, Cell anchor) {
        return new ShapeMask(width, height, depth, new BitSet(), anchor);
    }

    public static ShapeMask full(int width, int height, int depth, Cell anchor) {
        checkAxes(width, height, depth);
        BitSet bits = new BitSet(width * height * depth);
        bits.set(0, width * height * depth);
        return new ShapeMask(width, height, depth, bits, anchor);
    }

    /** Takes ownership of {@code bits}; callers in this package must not keep a reference. */
    static ShapeMask wrap(int width, int height, int depth, BitSet bits, Cell anchor) {
        return new ShapeMask(width, height, depth, bits, anchor);
    }

    static int index(int width, int height, int x, int y, int z) {
        return (z * height + y) * width + x;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int depth() {
        return depth;
    }

    public Cell anchor() {
        return anchor;
    }

    public int count() {
        return positions.size();
    }

    public boolean isEmpty() {
        return positions.isEmpty();
    }

    public boolean contains(int x, int y, int z) {
        return inBox(width, height, depth, x, y, z) && bits.get(index(width, height, x, y, z));
    }

    public boolean contains(Cell cell) {
        return contains(cell.x(), cell.y(), cell.z());
    }

    /** Selected cells, unmodifiable, ordered by z, then y, then x ascending. */
    public List<Cell> positions() {
        return positions;
    }

    /** The lowest z that contains a selected cell, or empty for an empty mask. */
    public OptionalInt frontLayer() {
        return positions.isEmpty() ? OptionalInt.empty() : OptionalInt.of(positions.get(0).z());
    }

    /** A fresh copy of the selection (layout: see class doc). */
    public BitSet toBitSet() {
        return (BitSet) bits.clone();
    }

    private static void checkAxes(int width, int height, int depth) {
        checkAxis("width", width);
        checkAxis("height", height);
        checkAxis("depth", depth);
    }

    private static void checkAxis(String name, int value) {
        if (value < 1 || value > ShapeLimits.TECHNICAL_MAX_AXIS) {
            throw new IllegalArgumentException(
                    name + " must be in 1.." + ShapeLimits.TECHNICAL_MAX_AXIS + ": " + value);
        }
    }

    private static void checkCellsInBox(BitSet bits, int size) {
        if (bits.length() > size) {
            throw new IllegalArgumentException("cell index " + (bits.length() - 1) + " outside the box");
        }
    }

    private static boolean inBox(int width, int height, int depth, int x, int y, int z) {
        return x >= 0 && x < width && y >= 0 && y < height && z >= 0 && z < depth;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ShapeMask other
                && width == other.width
                && height == other.height
                && depth == other.depth
                && anchor.equals(other.anchor)
                && bits.equals(other.bits);
    }

    @Override
    public int hashCode() {
        return Objects.hash(width, height, depth, anchor, bits);
    }

    @Override
    public String toString() {
        return "ShapeMask[" + width + "x" + height + "x" + depth + ", anchor=" + anchor + ", count=" + count() + "]";
    }
}
