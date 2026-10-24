package com.byflx.excavation3d.editor;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SizeRowStepTest {
    @Test void wheelHitsOnlyTheVisibleValueAndButtonsLeavingLabelsAndErrorsForScrolling() {
        var layout = EditorLayout.fit(320, 240, 6, 9);
        int right = 6 + layout.leftWidth();
        int left = right - 2 * 14 - 2 * 2 - 32;
        for (int y : new int[] {44, 88, 132}) {
            for (int x : new int[] {6, left - 1, right}) {
                assertFalse(SizeRowStep.hit(x, y + 10, left, right, y, 22, layout.contentBottom()));
            }
            for (int x : new int[] {left, left + 14 + 2, right - 1}) {
                assertTrue(SizeRowStep.hit(x, y, left, right, y, 22, layout.contentBottom()));
                assertTrue(SizeRowStep.hit(x, y + 19.99, left, right, y, 22, layout.contentBottom()));
                assertFalse(SizeRowStep.hit(x, y - 0.01, left, right, y, 22, layout.contentBottom()));
                assertFalse(SizeRowStep.hit(x, y + 20, left, right, y, 22, layout.contentBottom()));
                assertFalse(SizeRowStep.hit(x, y + 28, left, right, y, 22, layout.contentBottom()));
            }
        }
        // Scrolled, partially clipped controls cannot consume input while their buttons are hidden.
        assertFalse(SizeRowStep.hit(left, 22, left, right, 21, 22, layout.contentBottom()));
        assertFalse(SizeRowStep.hit(left, 168, left, right, 168, 22, layout.contentBottom()));
    }

    @Test void clickAndWheelStepsUseOneOrFiveAndClampAtBothBounds() {
        assertEquals(1, SizeRowStep.amount(false));
        assertEquals(5, SizeRowStep.amount(true));
        assertEquals(4, SizeRowStep.next(3, 1, false, 1, 32));
        assertEquals(2, SizeRowStep.next(3, -1, false, 1, 32));
        assertEquals(8, SizeRowStep.next(3, 1, true, 1, 32));
        assertEquals(1, SizeRowStep.next(3, -1, true, 1, 32));
        assertEquals(32, SizeRowStep.next(30, 1, true, 1, 32));
        assertEquals(32, SizeRowStep.next(32, 1, false, 1, 32));
        assertEquals(1, SizeRowStep.next(1, -1, false, 1, 32));
        assertEquals(3, SizeRowStep.next(3, 0, true, 1, 32));
        // A saved tool may exceed a newly lowered server limit; the first step must recover it.
        assertEquals(4, SizeRowStep.next(32, -1, false, 1, 4));
        assertEquals(4, SizeRowStep.next(32, 1, true, 1, 4));
        assertEquals(1, SizeRowStep.next(1, Integer.MIN_VALUE, true, 1, 32));
    }
}
