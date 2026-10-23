package com.byflx.excavation3d.editor;

import com.byflx.excavation3d.shape.*;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BuiltInShapesTest {
    @Test void definitionsHaveFixedOrderDimensionsAndDefaultStairs() {
        assertEquals(4, BuiltInShapes.ALL.size());
        assertEquals(new ShapeParams.Cuboid(1, 2, 1), BuiltInShapes.ALL.get(0).config().params());
        assertEquals(new ShapeParams.Cuboid(3, 3, 1), BuiltInShapes.ALL.get(1).config().params());
        assertEquals(new ShapeParams.Cuboid(5, 5, 1), BuiltInShapes.ALL.get(2).config().params());
        assertEquals(ShapeTemplates.defaults(ShapeType.STAIR), BuiltInShapes.ALL.get(3).config().params());
        for (var entry : BuiltInShapes.ALL) {
            assertInstanceOf(ShapeGenerator.Valid.class, ShapeGenerator.generate(entry.config(), ShapeLimits.DEFAULT));
            assertEquals(entry, BuiltInShapes.find(entry.id()).orElseThrow());
        }
    }

    @Test void downwardFloorHasTwentyFiveCellsAtOneWorldHeightForEveryFacing() {
        var generated = (ShapeGenerator.Valid) ShapeGenerator.generate(BuiltInShapes.ALL.get(2).config(), ShapeLimits.DEFAULT);
        var shape = generated.shape();
        assertEquals(25, shape.count());
        for (Direction facing : new Direction[] {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST}) {
            var orientation = Orientation.orient(Direction.UP, facing, Orientation.Mode.HIT_FACE);
            var offsets = new java.util.HashSet<Offset>();
            for (Cell cell : shape.mask().positions()) {
                Offset offset = orientation.toWorld(cell.x() - shape.anchor().x(), cell.y() - shape.anchor().y(), cell.z());
                assertEquals(0, offset.y());
                offsets.add(offset);
            }
            assertEquals(25, offsets.size());
            assertEquals(5, offsets.stream().map(Offset::x).distinct().count());
            assertEquals(5, offsets.stream().map(Offset::z).distinct().count());
        }
    }
}
