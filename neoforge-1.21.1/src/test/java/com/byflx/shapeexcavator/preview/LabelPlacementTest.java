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
    @Test
    void axisEndsAvoidTheShapeAndEachOtherAcrossCameraAnglesAndPanelSizes() {
        for (int panelWidth : new int[] {142, 164, 240, 600}) {
            Rect panel = new Rect(0, 0, panelWidth, 120);
            for (int yaw = -180; yaw < 180; yaw += 15) {
                for (int pitch : new int[] {10, 25, 65}) {
                    for (int scale : new int[] {6, 16, 48}) {
                        var camera = new PreviewPicking.Camera(panelWidth / 2.0, 60, scale, yaw, pitch, 5, 3, 1);
                        var origin = camera.project(new PreviewPicking.Vec(-0.05, -0.05, -0.05));
                        var ends = List.of(new PreviewPicking.Vec(5.6, -0.05, -0.05),
                                new PreviewPicking.Vec(-0.05, 3.6, -0.05), new PreviewPicking.Vec(-0.05, -0.05, 1.6));
                        var labels = ends.stream().map(camera::project).map(p -> new LabelPlacement.AxisLabel(
                                new Label((int) Math.round(p.x()), (int) Math.round(p.y()), 48, 10),
                                (int) Math.round(origin.x()), (int) Math.round(origin.y()))).toList();
                        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY;
                        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
                        for (int x : new int[] {0, 5}) for (int y : new int[] {0, 3}) for (int z : new int[] {0, 1}) {
                            var p = camera.project(new PreviewPicking.Vec(x, y, z));
                            minX = Math.min(minX, p.x()); minY = Math.min(minY, p.y());
                            maxX = Math.max(maxX, p.x()); maxY = Math.max(maxY, p.y());
                        }
                        Rect shape = new Rect((int) Math.floor(minX) - 3, (int) Math.floor(minY) - 3,
                                (int) Math.ceil(maxX) + 3, (int) Math.ceil(maxY) + 3);
                        var placed = LabelPlacement.axisEnds(labels, shape, panel);
                        assertEquals(3, placed.size());
                        for (int i = 0; i < placed.size(); i++) {
                            if (placed.get(i).isEmpty()) continue;
                            Rect r = placed.get(i).get();
                            assertTrue(r.inside(panel));
                            assertFalse(r.intersects(shape.inflate(2)));
                            var axis = labels.get(i);
                            double dx = axis.label().anchorX() - axis.originX();
                            double dy = axis.label().anchorY() - axis.originY();
                            for (int x : new int[] {r.x0(), r.x1()}) for (int y : new int[] {r.y0(), r.y1()}) {
                                assertTrue((x - axis.label().anchorX()) * dx + (y - axis.label().anchorY()) * dy > 0,
                                        "every corner must be beyond its own axis end");
                            }
                            for (int j = 0; j < i; j++) if (placed.get(j).isPresent()) {
                                assertFalse(r.intersects(placed.get(j).get().inflate(2)));
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    void crowdedOrOversizedAxisLabelsAreHiddenInsteadOfOverlapping() {
        var axis = new LabelPlacement.AxisLabel(new Label(80, 50, 40, 9), 50, 50);
        var placed = LabelPlacement.axisEnds(List.of(axis, axis), new Rect(30, 30, 70, 70), new Rect(0, 0, 130, 100));
        assertTrue(placed.get(0).isPresent());
        assertTrue(placed.get(1).isPresent());
        assertFalse(placed.get(0).get().intersects(placed.get(1).get()));
        assertTrue(LabelPlacement.axisEnds(List.of(axis), new Rect(-10, -10, 150, 120),
                new Rect(0, 0, 130, 100)).getFirst().isEmpty());
        assertTrue(LabelPlacement.axisEnds(List.of(axis), new Rect(0, 0, 1, 1),
                new Rect(0, 0, 30, 8)).getFirst().isEmpty());
    }

    @Test
    void edgeMidpointsKeepSeparateLabelsWithoutAFallbackStrip() {
        Rect panel = new Rect(170, 40, 311, 160);
        for (int y = 50; y < 220; y += 17) {
            var labels = List.of(new Label(240, y, 42, 9), new Label(190, y - 40, 54, 9),
                    new Label(235, y, 42, 9));
            var layout = LabelPlacement.edges(labels, panel);
            assertFalse(layout.clipsShape());
            for (int i = 0; i < 3; i++) {
                assertTrue(layout.labels().get(i).inside(panel));
                for (int j = 0; j < i; j++) {
                    assertFalse(layout.labels().get(i).intersects(layout.labels().get(j).inflate(6)));
                }
            }
        }
        var layout = LabelPlacement.edges(List.of(new Label(240, 100, 42, 9)), panel);
        assertEquals(new Rect(219, 96, 261, 105), layout.labels().getFirst());
    }
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

    @Test
    void frontViewLabelsNeverOverlapEachOther() {
        // Straight front view in a 320×240 GUI: the depth axis end and the "Front" arrow both project
        // onto the shape and want the free spot below it. Texts as in en_us/de_de.
        String[][] texts = {
                {"Width %s", "Height %s", "Depth %s", "Front → into the block"},
                {"Breite %s", "Höhe %s", "Tiefe %s", "Vorne → in den Block"}};
        int cx = 240;
        int cy = 101;
        for (String[] language : texts) {
            for (int w = 1; w <= 6; w++) {
                for (int scale = 4; scale <= 60; scale++) {
                    int h = 3;
                    int d = 1;
                    Rect shape = new Rect((int) Math.floor(cx - scale * w / 2.0), (int) Math.floor(cy - scale * h / 2.0),
                            (int) Math.ceil(cx + scale * w / 2.0), (int) Math.ceil(cy + scale * h / 2.0));
                    float anchorX = (w - 1) / 2 + 0.5F;
                    List<Label> labels = List.of(
                            label(language[0], w, cx + scale * (w + 0.9F - w / 2.0F), cy + scale * h / 2.0F),
                            label(language[1], h, cx - scale * w / 2.0F, cy - scale * (h + 0.9F - h / 2.0F)),
                            label(language[2], d, cx - scale * w / 2.0F, cy + scale * h / 2.0F),
                            label(language[3], 0, cx + scale * (anchorX - w / 2.0F), cy - scale * (1.5F - h / 2.0F)));
                    assertCalm(LabelPlacement.layout(labels, shape, SMALL_PANEL), shape, SMALL_PANEL);
                }
            }
        }
    }

    @Test
    void laterLabelMovesNextToAnEarlierOne() {
        Rect first = new Rect(80, 112, 120, 121);
        Rect label = LabelPlacement.place(100, 116, 40, 9, SHAPE, PANEL, List.of(first)).orElseThrow();
        assertFalse(label.intersects(first), label.toString());
        assertFalse(label.intersects(SHAPE), label.toString());
        assertTrue(label.inside(PANEL), label.toString());
    }

    private static Label label(String format, int value, float x, float y) {
        String text = format.replace("%s", Integer.toString(value));
        return new Label(Math.round(x), Math.round(y), width(text), 9);
    }

    /** Advance widths of the vanilla default font; glyphs outside ASCII are counted generously. */
    private static int width(String text) {
        int width = 0;
        for (char c : text.toCharArray()) {
            width += switch (c) {
                case ' ' -> 4;
                case 'i', '!', '.', ',', ':', ';', '|', '\'' -> 2;
                case 'l', '`' -> 3;
                case 't', 'I', '[', ']', '"' -> 4;
                case 'f', 'k', '<', '>', '(', ')', '{', '}', '*' -> 5;
                default -> c < 128 || Character.isLetter(c) ? 6 : 8;
            };
        }
        return width;
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
            for (int j = i + 1; j < rects.size(); j++) {
                assertFalse(label.intersects(rects.get(j)), label + " overlaps " + rects.get(j));
            }
        }
    }
}
