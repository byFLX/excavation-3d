package com.byflx.shapeexcavator.shape;

/** A cell in local shape coordinates: x = width, y = height, z = depth (0 is the front plane). */
public record Cell(int x, int y, int z) {
}
