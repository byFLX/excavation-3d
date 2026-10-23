package com.byflx.shapeexcavator.editor;

import com.byflx.shapeexcavator.shape.ShapeConfig;
import com.byflx.shapeexcavator.shape.ShapeParams;
import com.byflx.shapeexcavator.shape.ShapeTransform;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class QuickMenuLogicTest {
    @Test void crosshairAnchorAndEntireWheelFitAllEffectiveGuiScalesWithFooterSpace() {
        for (int windowWidth : new int[] {854, 1280, 1920}) {
            for (int windowHeight : new int[] {480, 720, 1080}) {
                for (int requestedScale = 1; requestedScale <= 4; requestedScale++) {
                    int scale = Math.min(requestedScale, Math.min(windowWidth / 320, windowHeight / 240));
                    int width = (int) Math.ceil((double) windowWidth / scale);
                    int height = (int) Math.ceil((double) windowHeight / scale);
                    for (int count : new int[] {0, 1, 4, 8, 9, 66}) {
                        var layout = QuickMenuLogic.layout(width, height, count, 1000);
                        assertEquals(width / 2, layout.centerX());
                        assertEquals(height / 2, layout.centerY());
                        assertTrue(layout.centerX() - layout.radius() - 5 >= 0);
                        assertTrue(layout.centerX() + layout.radius() + 5 <= width);
                        assertTrue(layout.centerY() - layout.radius() - 28 >= 0);
                        assertTrue(layout.footerY() + 24 + 9 <= height);
                        assertTrue(layout.deadZone() > 0 && layout.deadZone() < layout.radius());
                        assertEquals(-1, layout.highlighted(layout.centerX(), layout.centerY()));
                    }
                }
            }
        }
    }

    @Test void tapOpensEditorAndHoldOpensMenuExactlyOnce() {
        var hold = new QuickMenuLogic.Hold();
        assertEquals(QuickMenuLogic.Gesture.NONE, hold.update(true, 1000));
        assertEquals(QuickMenuLogic.Gesture.TAP, hold.update(false, 1249));
        assertEquals(QuickMenuLogic.Gesture.NONE, hold.update(false, 1250));
        hold.update(true, 2000);
        assertEquals(QuickMenuLogic.Gesture.NONE, hold.update(true, 2249));
        assertEquals(QuickMenuLogic.Gesture.HOLD, hold.update(true, 2250));
        assertEquals(QuickMenuLogic.Gesture.NONE, hold.update(true, 3000));
        assertEquals(QuickMenuLogic.Gesture.NONE, hold.update(false, 3100));
    }

    @Test void cancelWhileAnotherScreenIsOpenPreventsATapOnRelease() {
        var hold = new QuickMenuLogic.Hold();
        hold.update(true, 0); hold.cancel();
        assertEquals(QuickMenuLogic.Gesture.NONE, hold.update(false, 500));
    }

    @Test void anglesSelectClockwiseFromUpForEverySegmentCount() {
        for (int count = 1; count <= QuickMenuLogic.PAGE_SIZE; count++) {
            var layout = QuickMenuLogic.layout(640, 360, count, 0);
            for (int segment = 0; segment < count; segment++) {
                double angle = layout.angle(segment);
                assertEquals(segment, QuickMenuLogic.segmentAtAngle(angle, count));
                assertEquals(segment, QuickMenuLogic.segmentAtAngle(angle + Math.PI * 8, count));
                assertEquals(segment, QuickMenuLogic.segmentAtAngle(angle - Math.PI * 8, count));
                assertEquals(segment, layout.highlighted(layout.centerX() + Math.cos(angle) * 10000,
                        layout.centerY() + Math.sin(angle) * 10000));
                double boundary = angle + Math.PI / count;
                assertEquals(segment, QuickMenuLogic.segmentAtAngle(boundary - 0.000001, count));
                assertEquals((segment + 1) % count, QuickMenuLogic.segmentAtAngle(boundary + 0.000001, count));
            }
        }
        assertEquals(0, QuickMenuLogic.segmentAtAngle(-Math.PI / 2, 4));
        assertEquals(1, QuickMenuLogic.segmentAtAngle(0, 4));
        assertEquals(2, QuickMenuLogic.segmentAtAngle(Math.PI / 2, 4));
        assertEquals(3, QuickMenuLogic.segmentAtAngle(Math.PI, 4));
        assertEquals(-1, QuickMenuLogic.segmentAtAngle(0, 0));
        assertEquals(-1, QuickMenuLogic.segmentAtAngle(Double.NaN, 8));
        assertEquals(-1, QuickMenuLogic.segmentAtAngle(Double.POSITIVE_INFINITY, 8));
    }

    @Test void centreAndDeadZoneBoundaryCancelInEveryDirection() {
        var layout = QuickMenuLogic.layout(320, 240, 8, 0);
        assertEquals(-1, layout.highlighted(layout.centerX(), layout.centerY()));
        for (int segment = 0; segment < 8; segment++) {
            double a = layout.angle(segment);
            double inside = layout.deadZone() - 0.001;
            assertEquals(-1, layout.highlighted(layout.centerX() + Math.cos(a) * inside,
                    layout.centerY() + Math.sin(a) * inside));
            double outside = layout.deadZone() + 0.001;
            assertEquals(segment, layout.highlighted(layout.centerX() + Math.cos(a) * outside,
                    layout.centerY() + Math.sin(a) * outside));
        }
        assertEquals(-1, layout.highlighted(layout.centerX() + layout.deadZone(), layout.centerY()));
        assertEquals(-1, QuickMenuLogic.layout(320, 240, 0, 0).highlighted(0, 0));
    }

    @Test void labelBoundsStayInsideTheRingEvenAtTheSidesOnTheSmallestGui() {
        for (int height : new int[] {240, 360, 480, 1080}) {
            for (int count = 1; count <= 8; count++) {
                var layout = QuickMenuLogic.layout(427, height, count, 0);
                for (int segment = 0; segment < count; segment++) {
                    int halfWidth = layout.labelWidth(segment) / 2;
                    assertTrue(halfWidth >= 12, "At least a short name fits");
                    for (int x : new int[] {-halfWidth, halfWidth}) {
                        for (int y : new int[] {4, 13}) {
                            double distance = Math.hypot(layout.labelX(segment) + x - layout.centerX(),
                                    layout.labelY(segment) + y - layout.centerY());
                            assertTrue(distance < layout.radius() - 1);
                            assertTrue(distance > layout.deadZone());
                        }
                    }
                }
            }
        }
    }

    @Test void everyEntryIsReachableOnceAndPagesClampIncludingPartialLastPage() {
        for (int count = 1; count <= 66; count++) {
            boolean[] reached = new boolean[count];
            var first = QuickMenuLogic.layout(320, 240, count, -1);
            assertEquals(0, first.page());
            assertEquals((count + 7) / 8, first.pages());
            for (int page = 0; page < first.pages(); page++) {
                var layout = QuickMenuLogic.layout(320, 240, count, page);
                assertEquals(page * 8, layout.offset());
                assertTrue(layout.segments() > 0 && layout.segments() <= 8);
                for (int segment = 0; segment < layout.segments(); segment++) {
                    double a = layout.angle(segment);
                    int index = layout.highlighted(layout.centerX() + Math.cos(a) * layout.labelRadius(),
                            layout.centerY() + Math.sin(a) * layout.labelRadius());
                    assertEquals(layout.offset() + segment, index);
                    assertFalse(reached[index]);
                    reached[index] = true;
                }
            }
            for (boolean seen : reached) assertTrue(seen);
            assertEquals(first.pages() - 1, QuickMenuLogic.layout(320, 240, count, 1000).page());
        }
        assertEquals(1, QuickMenuLogic.layout(320, 240, 8, 0).pages());
        assertEquals(2, QuickMenuLogic.layout(320, 240, 9, 0).pages());
        assertEquals(2, QuickMenuLogic.layout(320, 240, 66, 100).segments());
    }

    @Test void offIsActiveOnlyWithoutAShapeAndOtherEntriesMatchByValue() {
        var off = new QuickMenuLogic.Entry(QuickMenuLogic.Kind.OFF, "");
        var last = new QuickMenuLogic.Entry(QuickMenuLogic.Kind.LAST, "");
        var preset = new QuickMenuLogic.Entry(QuickMenuLogic.Kind.PRESET, "Saved");
        var shape = ShapeConfig.of(new ShapeParams.Cuboid(5, 3, 1));
        var other = ShapeConfig.of(new ShapeParams.Cuboid(3, 3, 1));
        assertTrue(QuickMenuLogic.isActive(off, null, null));
        assertFalse(QuickMenuLogic.isActive(off, shape, null));
        for (var entry : List.of(last, preset)) {
            assertTrue(QuickMenuLogic.isActive(entry, shape.withRevision(42), shape.withRevision(0)));
            assertFalse(QuickMenuLogic.isActive(entry, shape, other));
            assertFalse(QuickMenuLogic.isActive(entry, shape, shape.withTransform(new ShapeTransform(1, false))));
            assertFalse(QuickMenuLogic.isActive(entry, null, shape));
            assertFalse(QuickMenuLogic.isActive(entry, shape, null));
            assertFalse(QuickMenuLogic.isActive(entry, null, null));
        }
    }

    @Test void labelsFitWithoutWrappingOrBreakingUnicodeAndHintsPreferCompactText() {
        java.util.function.ToIntFunction<String> width = s -> s.codePointCount(0, s.length()) * 6;
        assertEquals("Test", QuickMenuLogic.fitText("Test", 24, width));
        assertEquals("Te…", QuickMenuLogic.fitText("Test", 18, width));
        assertEquals("…", QuickMenuLogic.fitText("Test", 6, width));
        assertEquals("", QuickMenuLogic.fitText("Test", 5, width));
        assertEquals("", QuickMenuLogic.fitText("Test", 0, width));
        assertEquals("", QuickMenuLogic.fitText("Test", -1, width));
        assertEquals("A B C", QuickMenuLogic.fitText("A\nB\rC", 100, width));
        assertEquals("😀…", QuickMenuLogic.fitText("😀😀😀", 12, width));
        assertEquals("Full hint", QuickMenuLogic.fitHint("Full hint", "Short", 54, width));
        assertEquals("Short", QuickMenuLogic.fitHint("Full hint", "Short", 30, width));
        assertEquals("Sh…", QuickMenuLogic.fitHint("Full hint", "Short", 18, width));
        for (int budget = 0; budget < 300; budget++) {
            String fitted = QuickMenuLogic.fitText("Very long preset 😀 · 32 × 32 × 32 · 4096 blocks", budget, width);
            assertTrue(width.applyAsInt(fitted) <= budget);
            assertFalse(fitted.contains("\n"));
        }
    }

    @Test void lastShapeIsFirstOffIsLastAndPlayersNeverShareTheirLastShape() {
        var memory = new QuickMenuLogic.LastShape<String>();
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        assertEquals(List.of(new QuickMenuLogic.Entry(QuickMenuLogic.Kind.OFF, "")), QuickMenuLogic.entries(List.of(), false));
        memory.remember(a, "A"); memory.remember(b, "B"); memory.remember(a, null);
        assertEquals("A", memory.find(a).orElseThrow());
        assertEquals("B", memory.find(b).orElseThrow());
        var entries = QuickMenuLogic.entries(List.of("Saved one", "Saved two"), memory.find(a).isPresent());
        assertEquals(QuickMenuLogic.Kind.LAST, entries.getFirst().kind());
        assertEquals("Saved one", entries.get(1).name());
        assertEquals(QuickMenuLogic.Kind.OFF, entries.getLast().kind());
        memory.remember(a, "New");
        assertEquals("New", memory.find(a).orElseThrow());
        assertTrue(memory.find(UUID.randomUUID()).isEmpty());
    }

    @Test void onlyTheMatchingSuccessfulRemovalRemembersEvenAfterItsScreenWasClosed() {
        var memory = new QuickMenuLogic.LastShape<String>();
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        memory.expectRemoval(1, a, "A");
        memory.expectRemoval(2, b, "B");
        memory.onResult(99, true);
        assertTrue(memory.find(a).isEmpty());
        memory.onResult(1, false);
        memory.onResult(1, true); // Rejected requests cannot be accepted a second time.
        assertTrue(memory.find(a).isEmpty());
        memory.onResult(2, true);
        assertEquals("B", memory.find(b).orElseThrow());
        memory.expectRemoval(3, b, null); // Off again on an already empty tool keeps the old value.
        memory.onResult(3, true);
        assertEquals("B", memory.find(b).orElseThrow());
        memory.expectRemoval(4, a, "Old connection");
        memory.clearPending();
        memory.onResult(4, true);
        assertTrue(memory.find(a).isEmpty());
        assertEquals("B", memory.find(b).orElseThrow());
    }

    @Test void unansweredRemovalBookkeepingIsBounded() {
        var memory = new QuickMenuLogic.LastShape<Integer>();
        UUID player = UUID.randomUUID();
        for (int session = 1; session <= 65; session++) memory.expectRemoval(session, player, session);
        memory.onResult(1, true);
        assertTrue(memory.find(player).isEmpty());
        memory.onResult(65, true);
        assertEquals(65, memory.find(player).orElseThrow());
    }
}
