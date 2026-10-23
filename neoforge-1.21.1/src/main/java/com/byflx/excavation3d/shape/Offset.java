package com.byflx.excavation3d.shape;

/** A world-space block offset relative to the anchor (start) block. */
public record Offset(int x, int y, int z) {
    public static final Offset ZERO = new Offset(0, 0, 0);
}
