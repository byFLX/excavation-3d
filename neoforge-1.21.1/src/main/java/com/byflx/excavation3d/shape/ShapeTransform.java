package com.byflx.excavation3d.shape;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

/**
 * Explicit shape transform in the local width/height plane (plan §7). Mirroring on the width axis
 * ({@code x → W−1−x}) is applied first, then {@code rotation} clockwise quarter turns as seen looking
 * into the block, each mapping {@code (x, y) → (y, W−1−x)} and swapping width and height. Depth is
 * unchanged. The mask and its anchor go through the same mapping.
 */
public record ShapeTransform(int rotation, boolean mirrored) {
    public static final ShapeTransform IDENTITY = new ShapeTransform(0, false);

    public ShapeTransform {
        if (rotation < 0 || rotation > 3) {
            throw new IllegalArgumentException("rotation must be 0..3 quarter turns: " + rotation);
        }
    }

    /** All eight distinct transforms. */
    public static List<ShapeTransform> all() {
        List<ShapeTransform> all = new ArrayList<>(8);
        for (boolean mirrored : new boolean[] {false, true}) {
            for (int rotation = 0; rotation < 4; rotation++) {
                all.add(new ShapeTransform(rotation, mirrored));
            }
        }
        return List.copyOf(all);
    }

    /** This transform followed by one more clockwise quarter turn. */
    public ShapeTransform rotatedClockwise() {
        return new ShapeTransform((rotation + 1) % 4, mirrored);
    }

    /** Toggles the mirror flag, keeping the rotation. */
    public ShapeTransform toggledMirror() {
        return new ShapeTransform(rotation, !mirrored);
    }

    public boolean isIdentity() {
        return rotation == 0 && !mirrored;
    }

    public ShapeMask apply(ShapeMask mask) {
        if (isIdentity()) {
            return mask;
        }
        int width = mask.width();
        int height = mask.height();
        boolean swapped = rotation % 2 == 1;
        int newWidth = swapped ? height : width;
        int newHeight = swapped ? width : height;
        BitSet bits = new BitSet(newWidth * newHeight * mask.depth());
        for (Cell cell : mask.positions()) {
            Cell mapped = map(cell, width, height);
            bits.set(ShapeMask.index(newWidth, newHeight, mapped.x(), mapped.y(), mapped.z()));
        }
        return ShapeMask.wrap(newWidth, newHeight, mask.depth(), bits, map(mask.anchor(), width, height));
    }

    private Cell map(Cell cell, int width, int height) {
        int x = mirrored ? width - 1 - cell.x() : cell.x();
        int y = cell.y();
        int currentWidth = width;
        int currentHeight = height;
        for (int turn = 0; turn < rotation; turn++) {
            int rotatedX = y;
            int rotatedY = currentWidth - 1 - x;
            x = rotatedX;
            y = rotatedY;
            int previousWidth = currentWidth;
            currentWidth = currentHeight;
            currentHeight = previousWidth;
        }
        return new Cell(x, y, cell.z());
    }
}
