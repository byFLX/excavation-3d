package com.byflx.excavation3d.editor;

import com.byflx.excavation3d.shape.ShapeConfig;
import com.byflx.excavation3d.shape.ShapeParams;
import com.byflx.excavation3d.shape.ShapeTransform;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class QuickMenuLogicTest {
    @Test void crosshairAnchorAndEntireWheelFitAllEffectiveGuiScales() {
        for (int windowWidth : new int[] {854, 1280, 1920}) {
            for (int windowHeight : new int[] {480, 720, 1080}) {
                for (int requestedScale = 1; requestedScale <= 4; requestedScale++) {
                    int scale = Math.min(requestedScale, Math.min(windowWidth / 320, windowHeight / 240));
                    int width = (int) Math.ceil((double) windowWidth / scale);
                    int height = (int) Math.ceil((double) windowHeight / scale);
                    for (int count : new int[] {0, 1, 4, 8, 9, 66}) {
                        var layout = QuickMenuLogic.layout(width, height, count, 1000);
                        assertEquals(width / 2.0, layout.centerX());
                        assertEquals(height / 2.0, layout.centerY());
                        assertTrue(layout.centerX() - 102 * layout.scale() >= 0);
                        assertTrue(layout.centerX() + 102 * layout.scale() <= width);
                        assertTrue(layout.centerY() - 102 * layout.scale() >= 0);
                        if (layout.showInstructions()) {
                            assertTrue(layout.instructionsY() >= 6);
                            assertTrue(layout.instructionsY() + 19 * layout.scale() < layout.centerY() - layout.radius());
                        }
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
        assertEquals(-1, layout.highlighted(Double.NaN, 0));
        assertEquals(-1, layout.highlighted(0, Double.POSITIVE_INFINITY));
    }

    @Test void namesStayInsideTheirOwnSectorIncludingDiagonalBorders() {
        for (int height : new int[] {120, 240, 360, 1080}) {
            for (int count = 1; count <= 8; count++) {
                var layout = QuickMenuLogic.layout(427, height, count, 0);
                for (int segment = 0; segment < count; segment++) {
                    double halfWidth = layout.labelWidth(segment) / 2;
                    assertTrue(halfWidth >= 6 * layout.scale(), "A truncated label fits");
                    for (int column = -4; column <= 4; column++) {
                        for (int row = 7; row <= 15; row++) {
                            double dx = layout.labelX(segment) + halfWidth * column / 4 - layout.centerX();
                            double dy = layout.labelY(segment) + row * layout.scale() - layout.centerY();
                            double distance = Math.hypot(dx, dy);
                            assertTrue(distance < layout.radius() - layout.scale());
                            assertTrue(distance > layout.deadZone() + layout.scale());
                            assertEquals(segment, QuickMenuLogic.segmentAtAngle(Math.atan2(dy, dx), count));
                        }
                    }
                }
            }
        }
    }

    @Test void ringUsesRequestedRadiiAndShrinksProportionallyWithoutMovingTheCrosshair() {
        var normal = QuickMenuLogic.layout(640, 360, 8, 0);
        assertEquals(88, normal.radius());
        assertEquals(28, normal.deadZone());
        for (int[] size : new int[][] {{320, 240}, {321, 241}, {160, 120}, {80, 60}}) {
            var layout = QuickMenuLogic.layout(size[0], size[1], 8, 0);
            assertEquals(size[0] / 2.0, layout.centerX());
            assertEquals(size[1] / 2.0, layout.centerY());
            assertEquals(28.0 / 88, layout.deadZone() / layout.radius(), 0.000001);
            assertTrue(layout.radius() <= 88);
            assertTrue(layout.centerX() - 102 * layout.scale() >= 0);
            assertTrue(layout.centerY() - 102 * layout.scale() >= 0);
            assertFalse(layout.showInstructions());
        }
    }

    @Test void numberKeysMapOnlyToVisibleEntriesOnEachPage() {
        for (int count = 0; count <= 66; count++) {
            int pages = QuickMenuLogic.layout(320, 240, count, 0).pages();
            for (int page = 0; page < pages; page++) {
                var layout = QuickMenuLogic.layout(320, 240, count, page);
                for (int number = -1; number <= 9; number++) {
                    assertEquals(number >= 1 && number <= layout.segments() ? (number == 1 ? 0 : page * 7 + number - 1) : -1,
                            layout.numberSelection(number));
                }
            }
        }
    }

    @Test void animationProgressClampsBeforeAndAfterItsDuration() {
        for (long duration : new long[] {QuickMenuLogic.OPEN_MS, QuickMenuLogic.HOVER_MS}) {
            assertEquals(0, QuickMenuLogic.animationProgress(900, 1000, duration));
            assertEquals(0, QuickMenuLogic.animationProgress(1000, 1000, duration));
            assertEquals(0.5, QuickMenuLogic.animationProgress(1000 + duration / 2, 1000, duration));
            assertEquals(1, QuickMenuLogic.animationProgress(1000 + duration, 1000, duration));
            assertEquals(1, QuickMenuLogic.animationProgress(Long.MAX_VALUE, 1000, duration));
        }
        assertEquals(1, QuickMenuLogic.animationProgress(1001, 1000, 0));
        assertEquals(1, QuickMenuLogic.animationProgress(1001, 1000, -1));
        assertEquals(0, QuickMenuLogic.animationProgress(1000, 1000, 0));
    }

    @Test void offIsPinnedOnEveryPageAndOtherEntriesAreReachableOnce() {
        for (int count = 1; count <= 66; count++) {
            boolean[] reached = new boolean[count];
            var first = QuickMenuLogic.layout(320, 240, count, -1);
            assertEquals(0, first.page());
            assertEquals(Math.max(1, (count - 1 + 6) / 7), first.pages());
            for (int page = 0; page < first.pages(); page++) {
                var layout = QuickMenuLogic.layout(320, 240, count, page);
                assertEquals(page * 7, layout.offset());
                assertTrue(layout.segments() > 0 && layout.segments() <= 8);
                for (int segment = 0; segment < layout.segments(); segment++) {
                    double a = layout.angle(segment);
                    int index = layout.highlighted(layout.centerX() + Math.cos(a) * layout.labelRadius(),
                            layout.centerY() + Math.sin(a) * layout.labelRadius());
                    assertEquals(segment == 0 ? 0 : layout.offset() + segment, index);
                    if (index != 0) assertFalse(reached[index]);
                    reached[index] = true;
                }
            }
            for (boolean seen : reached) assertTrue(seen);
            assertEquals(first.pages() - 1, QuickMenuLogic.layout(320, 240, count, 1000).page());
        }
        assertEquals(1, QuickMenuLogic.layout(320, 240, 8, 0).pages());
        assertEquals(2, QuickMenuLogic.layout(320, 240, 9, 0).pages());
        assertEquals(3, QuickMenuLogic.layout(320, 240, 66, 100).segments());
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

    @Test void truncationUsesPixelMeasurementsRatherThanCharacterCount() {
        java.util.function.ToIntFunction<String> width = s -> s.codePoints()
                .map(c -> c == 'W' ? 8 : c == '…' ? 5 : 2).sum();
        assertEquals("W…", QuickMenuLogic.fitText("WWii", 13, width));
        assertEquals("iiii", QuickMenuLogic.fitText("iiii", 8, width));
        assertEquals("…", QuickMenuLogic.fitText("WWii", 12, width));
        assertEquals("", QuickMenuLogic.fitText("WWii", 4, width));
        for (int budget = 0; budget < 32; budget++) {
            assertTrue(width.applyAsInt(QuickMenuLogic.fitText("WWiiWW", budget, width)) <= budget);
        }
    }










    @Test void wheelOrderIsOffThenOnlyFavoritesInSaveOrder() {
        var names = List.of("Stairs", "Zulu", "Alpha", "Tunnel 1×2", "Beta");
        var entries = QuickMenuLogic.entries(names);
        assertEquals(QuickMenuLogic.Kind.OFF, entries.getFirst().kind());
        assertEquals(names, entries.stream().skip(1).map(QuickMenuLogic.Entry::name).toList());
        assertTrue(entries.stream().skip(1).allMatch(e -> e.kind() == QuickMenuLogic.Kind.PRESET));
        var empty = QuickMenuLogic.entries(List.of());
        assertEquals(List.of(QuickMenuLogic.Kind.OFF, QuickMenuLogic.Kind.HINT), empty.stream().map(QuickMenuLogic.Entry::kind).toList());
        assertEquals(-1, QuickMenuLogic.activeIndex(empty, ShapeConfig.of(new ShapeParams.Cuboid(1, 1, 1)),
                true, n -> { throw new AssertionError("The hint must not look up a favorite"); }));
    }

    @Test void firstSegmentOffersOnOnlyForAPausedStoredShape() {
        assertFalse(QuickMenuLogic.firstSegmentOn(false, false));
        assertFalse(QuickMenuLogic.firstSegmentOn(false, true));
        assertFalse(QuickMenuLogic.firstSegmentOn(true, true));
        assertTrue(QuickMenuLogic.firstSegmentOn(true, false));
    }

    @Test void activeDotIsOffWhenPausedOtherwiseFirstMatchingShapeOrNone() {
        var shape = ShapeConfig.of(new ShapeParams.Cuboid(4, 4, 1));
        var menu = QuickMenuLogic.entries(List.of("Zulu", "Alpha"));
        java.util.function.Function<String, java.util.Optional<ShapeConfig>> lookup = n -> java.util.Optional.of(shape);
        assertEquals(0, QuickMenuLogic.activeIndex(menu, null, true, lookup));
        assertEquals(0, QuickMenuLogic.activeIndex(menu, shape, false, lookup));
        assertEquals(1, QuickMenuLogic.activeIndex(menu, shape.withRevision(42), true, lookup));
        assertEquals(-1, QuickMenuLogic.activeIndex(menu, shape.withTransform(new ShapeTransform(1, false)), true, lookup));
        assertEquals(-1, QuickMenuLogic.activeIndex(menu, shape, true, n -> java.util.Optional.empty()));
    }

    @Test void deletionNeedsTwoClicksOnTheSameFavoriteAndMovingAwayCancels() {
        var state = new QuickMenuLogic.FavoriteDelete();
        var a = new QuickMenuLogic.Entry(QuickMenuLogic.Kind.PRESET, "A");
        var b = new QuickMenuLogic.Entry(QuickMenuLogic.Kind.PRESET, "B");
        assertFalse(state.rightClick(a));
        state.hover(a);
        assertEquals("A", state.pending());
        assertTrue(state.rightClick(a));
        assertNull(state.pending());
        assertFalse(state.rightClick(a));
        state.hover(b);
        assertNull(state.pending());
        assertFalse(state.rightClick(a));
        state.hover(null);
        assertFalse(state.rightClick(a));
        assertFalse(state.rightClick(b));
        assertTrue(state.rightClick(b));
        for (var entry : QuickMenuLogic.entries(List.of())) {
            assertFalse(state.rightClick(entry));
            assertFalse(state.rightClick(entry));
            assertNull(state.pending());
        }
        for (String name : List.of("Tunnel 1×2", "Tunnel 3×3", "Floor 5×5", "Stairs")) {
            var entry = QuickMenuLogic.entries(List.of(name)).get(1);
            assertFalse(state.rightClick(entry));
            assertTrue(state.rightClick(entry));
        }
    }
}
