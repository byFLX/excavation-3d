package com.byflx.excavation3d.shape;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class EncodedShapeTest {
    private static ShapeMask lShape() {
        boolean[][][] cells = new boolean[3][2][2];
        cells[0][0][0] = true;
        cells[1][0][0] = true;
        cells[2][0][0] = true;
        cells[0][1][1] = true;
        return ShapeMask.of(cells, new Cell(1, 0, 0));
    }

    private static ShapeConfig decodeOk(EncodedShape encoded, long revision) {
        return assertInstanceOf(EncodedShape.Ok.class, encoded.decode(revision)).config();
    }

    @Test
    void everyTypeAndTransformRoundTrips() {
        List<ShapeParams> params = List.of(
                new ShapeParams.Cuboid(7, 5, 2),
                new ShapeParams.Plus(5, 4, 2, 2),
                new ShapeParams.Stair(3, 4, 2, 1, false),
                new ShapeParams.Corridor(3, 3, 6, 1, 1, true),
                new ShapeParams.Custom(lShape()),
                new ShapeParams.Custom(lShape(), Orientation.Mode.PLAYER_HORIZONTAL),
                new ShapeParams.Custom(ShapeMask.full(32, 32, 32, new Cell(0, 0, 0))));
        for (ShapeParams p : params) {
            for (ShapeTransform transform : ShapeTransform.all()) {
                ShapeConfig config = new ShapeConfig(ShapeConfig.FORMAT_VERSION, p, transform, 9L);
                assertEquals(config, decodeOk(EncodedShape.encode(config), 9L), p + " " + transform);
            }
        }
    }

    @Test
    void semanticallyInvalidValuesSurviveDecodingForTheValidator() {
        ShapeConfig zeroWidth = ShapeConfig.of(new ShapeParams.Cuboid(0, -3, Integer.MAX_VALUE));
        assertEquals(zeroWidth, decodeOk(EncodedShape.encode(zeroWidth), 0L));
        ShapeConfig empty = ShapeConfig.of(ShapeTemplates.emptyCustom(4, 4, 4));
        assertEquals(empty, decodeOk(EncodedShape.encode(empty), 0L));
    }

    @Test
    void customMaskWordsArePaddedToTheBox() {
        // Only the first cell is set, so BitSet.toLongArray() alone would give 1 word instead of 2.
        BitSet bits = new BitSet();
        bits.set(0);
        ShapeConfig config = ShapeConfig.of(new ShapeParams.Custom(ShapeMask.of(10, 10, 1, bits, new Cell(0, 0, 0))));
        EncodedShape encoded = EncodedShape.encode(config);
        assertEquals(2, encoded.maskWords().size());
        assertEquals(config, decodeOk(encoded, 0L));
    }

    @Test
    void structurallyBrokenDataIsAnErrorNotAnException() {
        EncodedShape good = EncodedShape.encode(ShapeConfig.of(new ShapeParams.Custom(lShape())));
        List<EncodedShape> broken = new ArrayList<>();
        broken.add(new EncodedShape(2, ShapeType.CUBOID, List.of(1, 1, 1), List.of(), 0, false));
        broken.add(new EncodedShape(1, ShapeType.CUBOID, List.of(1, 1), List.of(), 0, false));
        broken.add(new EncodedShape(1, ShapeType.CUBOID, List.of(1, 1, 1), List.of(1L), 0, false));
        broken.add(new EncodedShape(1, ShapeType.CUBOID, List.of(1, 1, 1), List.of(), 4, false));
        broken.add(new EncodedShape(1, ShapeType.CUBOID, List.of(1, 1, 1), List.of(), -1, false));
        broken.add(new EncodedShape(1, ShapeType.STAIR, List.of(1, 1, 1, 1, 2), List.of(), 0, false));
        broken.add(new EncodedShape(1, ShapeType.CORRIDOR, List.of(1, 1, 1, 1, 1, -1), List.of(), 0, false));
        // Custom: oversized box, anchor outside, wrong word count, bits beyond the box.
        broken.add(new EncodedShape(1, ShapeType.CUSTOM, List.of(33, 1, 1, 0, 0, 0), List.of(1L), 0, false));
        broken.add(new EncodedShape(1, ShapeType.CUSTOM, List.of(0, 1, 1, 0, 0, 0), List.of(), 0, false));
        broken.add(new EncodedShape(1, ShapeType.CUSTOM, List.of(3, 2, 2, 5, 0, 0), good.maskWords(), 0, false));
        broken.add(new EncodedShape(1, ShapeType.CUSTOM, List.of(3, 2, 2, 1, 0, 0), List.of(1L, 0L), 0, false));
        broken.add(new EncodedShape(1, ShapeType.CUSTOM, List.of(3, 2, 2, 1, 0, 0), List.of(1L << 40), 0, false));
        broken.add(new EncodedShape(1, ShapeType.CUSTOM,
                List.of(Integer.MAX_VALUE, Integer.MAX_VALUE, 2, 0, 0, 0), List.of(), 0, false));
        for (EncodedShape encoded : broken) {
            assertInstanceOf(EncodedShape.Error.class, encoded.decode(0L), encoded.toString());
        }
    }

    @Test
    void maximumMaskWordCountCoversTheTechnicalBox() {
        assertEquals(512, EncodedShape.MAX_MASK_WORDS);
    }
}
