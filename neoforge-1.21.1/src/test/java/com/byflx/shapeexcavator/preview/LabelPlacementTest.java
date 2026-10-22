package com.byflx.shapeexcavator.preview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.byflx.shapeexcavator.preview.LabelPlacement.Rect;
import org.junit.jupiter.api.Test;

class LabelPlacementTest {
    private static final Rect PANEL = new Rect(0, 0, 200, 150);
    private static final Rect SHAPE = new Rect(60, 40, 140, 110);

    @Test
    void freeSpotIsKept() {
        Rect label = LabelPlacement.place(30, 20, 40, 9, SHAPE, PANEL);
        assertEquals(new Rect(10, 16, 50, 25), label);
    }

    @Test
    void labelOnTheShapeMovesToTheNearestFreeSide() {
        Rect label = LabelPlacement.place(135, 75, 40, 9, SHAPE, PANEL);
        assertFalse(label.intersects(SHAPE));
        assertTrue(label.inside(PANEL));
        assertTrue(label.x0() >= SHAPE.x1(), label.toString());
    }

    @Test
    void labelsNeverCoverTheShapeAndStayInThePanel() {
        for (int x = -20; x <= 220; x += 7) {
            for (int y = -20; y <= 170; y += 5) {
                Rect label = LabelPlacement.place(x, y, 40, 9, SHAPE, PANEL);
                assertFalse(label.intersects(SHAPE), x + "," + y + " -> " + label);
                assertTrue(label.inside(PANEL), x + "," + y + " -> " + label);
            }
        }
    }
}
