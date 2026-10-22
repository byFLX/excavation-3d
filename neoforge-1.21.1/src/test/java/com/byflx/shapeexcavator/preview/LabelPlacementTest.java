package com.byflx.shapeexcavator.preview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.byflx.shapeexcavator.preview.LabelPlacement.Label;
import com.byflx.shapeexcavator.preview.LabelPlacement.Layout;
import com.byflx.shapeexcavator.preview.LabelPlacement.Rect;
import java.util.List;
import org.junit.jupiter.api.Test;

class LabelPlacementTest {
    private static final Rect PANEL = new Rect(0, 0, 200, 150);
    private static final Rect SHAPE = new Rect(60, 40, 140, 110);
    /** The label panel of the editor preview at a 320×240 GUI size. */
    private static final Rect SMALL_PANEL = new Rect(169, 37, 311, 167);

    @Test
    void freeSpotIsKept() {
        Rect label = LabelPlacement.place(30, 20, 40, 9, SHAPE, PANEL).orElseThrow();
        assertEquals(new Rect(10, 16, 50, 25), label);
    }

    @Test
    void labelOnTheShapeMovesToTheNearestFreeSide() {
        Rect label = LabelPlacement.place(135, 75, 40, 9, SHAPE, PANEL).orElseThrow();
        assertFalse(label.intersects(SHAPE));
        assertTrue(label.inside(PANEL));
        assertTrue(label.x0() >= SHAPE.x1(), label.toString());
    }

    @Test
    void labelsNeverCoverTheShapeAndStayInThePanel() {
        for (int x = -20; x <= 220; x += 7) {
            for (int y = -20; y <= 170; y += 5) {
                Rect label = LabelPlacement.place(x, y, 40, 9, SHAPE, PANEL).orElseThrow();
                assertFalse(label.intersects(SHAPE), x + "," + y + " -> " + label);
                assertTrue(label.inside(PANEL), x + "," + y + " -> " + label);
            }
        }
    }

    @Test
    void shapeCoveringThePanelHasNoFreeSpot() {
        Rect huge = new Rect(100, 0, 400, 300);
        assertTrue(LabelPlacement.place(240, 100, 40, 9, huge, SMALL_PANEL).isEmpty());
    }

    @Test
    void labelsBesideTheShapeNeedNoClip() {
        Layout layout = LabelPlacement.layout(labels(140, 75, 100, 30, 70, 120), SHAPE, PANEL);
        assertFalse(layout.clipsShape());
        assertCalm(layout, SHAPE, PANEL);
    }

    @Test
    void zoomedInShapeSendsTheLabelsToAStripBelowTheClippedShape() {
        // A 3×3×1 shape zoomed to 4× in a 320×240 GUI: its bounds cover the whole label panel.
        Rect zoomed = new Rect(120, -20, 360, 230);
        Layout layout = LabelPlacement.layout(labels(300, 160, 180, 40, 200, 150), zoomed, SMALL_PANEL);
        assertTrue(layout.clipsShape());
        assertTrue(layout.shapeMaxY() > SMALL_PANEL.y0(), "the shape keeps most of the panel");
        assertCalm(layout, zoomed, SMALL_PANEL);
    }

    @Test
    void narrowPanelWrapsTheStripIntoRows() {
        Rect panel = new Rect(0, 0, 60, 100);
        Layout layout = LabelPlacement.layout(labels(30, 50, 30, 50, 30, 50), new Rect(-10, -10, 70, 110), panel);
        assertTrue(layout.clipsShape());
        assertTrue(layout.labels().get(0).y0() < layout.labels().get(2).y0(), layout.toString());
        assertCalm(layout, new Rect(-10, -10, 70, 110), panel);
    }

    @Test
    void anyZoomKeepsTheLabelsOffTheShape() {
        int cx = (SMALL_PANEL.x0() + SMALL_PANEL.x1()) / 2;
        int cy = (SMALL_PANEL.y0() + SMALL_PANEL.y1()) / 2;
        for (int half = 4; half <= 300; half += 3) {
            Rect shape = new Rect(cx - half, cy - half * 3 / 4, cx + half, cy + half * 3 / 4);
            List<Label> labels = labels(shape.x1() + 4, shape.y1(), shape.x0(), shape.y0() - 4,
                    shape.x0() + 6, shape.y1() + 2);
            assertCalm(LabelPlacement.layout(labels, shape, SMALL_PANEL), shape, SMALL_PANEL);
        }
    }

    /** Width, height and depth labels at the given anchors, plus a short "Front" label at the centre. */
    private static List<Label> labels(int wx, int wy, int hx, int hy, int dx, int dy) {
        return List.of(new Label(wx, wy, 40, 9), new Label(hx, hy, 45, 9), new Label(dx, dy, 40, 9),
                new Label((wx + hx) / 2, (wy + hy) / 2, 28, 9));
    }

    /** No label covers the drawn (possibly clipped) shape, labels stay in the panel and never overlap. */
    private static void assertCalm(Layout layout, Rect shape, Rect panel) {
        Rect drawn = new Rect(shape.x0(), shape.y0(), shape.x1(), Math.min(shape.y1(), layout.shapeMaxY()));
        List<Rect> rects = layout.labels();
        for (int i = 0; i < rects.size(); i++) {
            Rect label = rects.get(i);
            if (drawn.y0() < drawn.y1()) {
                assertFalse(label.intersects(drawn), label + " covers " + drawn);
            }
            assertTrue(label.inside(panel), label + " outside " + panel);
            if (layout.clipsShape()) {
                for (int j = i + 1; j < rects.size(); j++) {
                    assertFalse(label.intersects(rects.get(j)), label + " overlaps " + rects.get(j));
                }
            }
        }
    }
}
