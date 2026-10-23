package com.byflx.excavation3d.editor;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SizeRowStepTest {
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
