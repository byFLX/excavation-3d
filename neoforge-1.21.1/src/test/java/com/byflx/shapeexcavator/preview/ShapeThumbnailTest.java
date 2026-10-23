package com.byflx.shapeexcavator.preview;

import com.byflx.shapeexcavator.shape.Cell;
import com.byflx.shapeexcavator.shape.ShapeConfig;
import com.byflx.shapeexcavator.shape.ShapeGenerator;
import com.byflx.shapeexcavator.shape.ShapeLimits;
import com.byflx.shapeexcavator.shape.ShapeMask;
import com.byflx.shapeexcavator.shape.ShapeParams;
import com.byflx.shapeexcavator.shape.ShapeTransform;
import java.util.BitSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ShapeThumbnailTest {
    @Test void everySurfaceVertexFitsInsideTheRequestedRadiusForAllShapesAndTransforms() {
        BitSet bits = new BitSet();
        bits.set(0); bits.set(1); bits.set(4); bits.set(22);
        var custom = ShapeMask.of(5, 3, 2, bits, new Cell(0, 0, 0));
        List<ShapeParams> params = List.of(new ShapeParams.Cuboid(1, 1, 1),
                new ShapeParams.Cuboid(32, 1, 1), new ShapeParams.Cuboid(1, 32, 1),
                new ShapeParams.Cuboid(4, 4, 1), new ShapeParams.Cuboid(16, 16, 16),
                new ShapeParams.Plus(7, 7, 3, 1), new ShapeParams.Stair(3, 5, 1, 1, true),
                new ShapeParams.Corridor(3, 3, 5, 1, 1, false), new ShapeParams.Custom(custom));
        for (var p : params) for (var transform : ShapeTransform.all()) {
            var result = ShapeGenerator.generate(ShapeConfig.of(p, transform), ShapeLimits.DEFAULT);
            assertInstanceOf(ShapeGenerator.Valid.class, result);
            var thumbnail = ShapeThumbnail.of(((ShapeGenerator.Valid) result).shape().mask());
            assertFalse(thumbnail.faces().isEmpty());
            for (double radius : new double[] {1, 6, 12, 24, 100}) {
                for (var face : thumbnail.faces()) {
                    assertEquals(4, face.points().size());
                    for (var point : face.points()) {
                        assertTrue(Double.isFinite(point.x()) && Double.isFinite(point.y()));
                        assertTrue(Math.hypot(point.x() * radius, point.y() * radius) <= radius + 1e-9);
                    }
                }
            }
        }
    }

    @Test void onlySelectedCellsHaveFacesAndInternalFacesAreOmitted() {
        var single = ShapeThumbnail.of(ShapeMask.full(1, 1, 1, new Cell(0, 0, 0)));
        assertEquals(3, single.faces().size());
        var solid = ShapeThumbnail.of(ShapeMask.full(2, 1, 1, new Cell(0, 0, 0)));
        assertEquals(5, solid.faces().size());
        BitSet bits = new BitSet(); bits.set(0); bits.set(2);
        var hole = ShapeThumbnail.of(ShapeMask.of(3, 1, 1, bits, new Cell(0, 0, 0)));
        assertEquals(6, hole.faces().size());
        assertEquals(3, single.faces().stream().map(ShapeThumbnail.Face::rgb).distinct().count());
        assertTrue(ShapeThumbnail.of(ShapeMask.empty(3, 2, 1, new Cell(0, 0, 0))).faces().isEmpty());
    }
}
