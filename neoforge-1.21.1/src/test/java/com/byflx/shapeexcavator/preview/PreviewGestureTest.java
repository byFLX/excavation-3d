package com.byflx.shapeexcavator.preview;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class PreviewGestureTest {
    @Test void physicalReleaseEndsOwnershipOnMoveWithoutAReleaseOrDragEvent() {
        for (int button : new int[] {0, 1}) {
            for (boolean dragged : new boolean[] {false, true}) {
                var gesture = new PreviewGesture();
                gesture.press(button, 50, 60, true);
                if (dragged) gesture.drag(button, 80, 90, 30, 30, true);
                assertFalse(gesture.endIfReleased(true));
                assertEquals(button, gesture.button());
                assertTrue(gesture.endIfReleased(false));
                assertEquals(-1, gesture.button());
                assertFalse(gesture.drag(button, 200, 200, 120, 110, true).consumed());
                assertEquals(PreviewGesture.Release.NONE, gesture.release(button, 200, 200, true, 1000));
                assertFalse(gesture.endIfReleased(false));
            }
        }
    }

    @Test void staleOwnershipEndsEvenWhenTheDragEventHasNoMatchingButton() {
        for (int eventButton : new int[] {-1, 1}) {
            var gesture = new PreviewGesture();
            gesture.press(0, 50, 60, true);
            assertFalse(gesture.drag(eventButton, 200, 200, 150, 140, false).consumed());
            assertEquals(-1, gesture.button());
        }
    }

    @Test void outsideWidgetPressCancelsPreviewAndDoesNotJoinItsDoubleClick() {
        var gesture = new PreviewGesture();
        gesture.press(0, 50, 60, true);
        assertEquals(PreviewGesture.Release.CLICK, gesture.release(0, 50, 60, true, 1000));
        gesture.press(0, 50, 60, true); // Release event gets lost.
        assertFalse(gesture.press(0, 200, 200, false));
        assertEquals(-1, gesture.button());
        assertFalse(gesture.drag(0, 200, 200, 150, 140, true).consumed());
        assertEquals(PreviewGesture.Release.NONE, gesture.release(0, 200, 200, false, 1100));
        gesture.press(0, 50, 60, true);
        assertEquals(PreviewGesture.Release.CLICK, gesture.release(0, 50, 60, true, 1200));
    }

    @Test void idleButtonPollingPreservesDoubleClickButAnAbandonedGestureClearsIt() {
        var gesture = new PreviewGesture();
        gesture.press(0, 50, 60, true);
        assertEquals(PreviewGesture.Release.CLICK, gesture.release(0, 50, 60, true, 1000));
        assertFalse(gesture.endIfReleased(false));
        gesture.press(0, 50, 60, true);
        assertEquals(PreviewGesture.Release.RESET, gesture.release(0, 50, 60, true, 1100));
        gesture.press(0, 50, 60, true);
        assertEquals(PreviewGesture.Release.CLICK, gesture.release(0, 50, 60, true, 1200));
        gesture.press(0, 50, 60, true);
        assertTrue(gesture.endIfReleased(false));
        gesture.press(0, 50, 60, true);
        assertEquals(PreviewGesture.Release.CLICK, gesture.release(0, 50, 60, true, 1300));
    }

    @Test void stationaryClickEndsOwnershipBeforeTheEditAndCannotRotateLater() {
        var gesture = new PreviewGesture();
        assertTrue(gesture.press(0, 50, 60, true));
        assertEquals(PreviewGesture.Release.CLICK, gesture.release(0, 50, 60, true, 1000));
        assertEquals(-1, gesture.button());
        assertFalse(gesture.drag(0, 200, 200, 150, 140, false).consumed());
        assertFalse(gesture.drag(-1, 200, 200, 150, 140, false).consumed());
        assertEquals(PreviewGesture.Release.NONE, gesture.release(0, 50, 60, true, 1001));
    }

    @Test void onlyThePressedButtonDragsAndReleaseOutsideEndsTheDrag() {
        var gesture = new PreviewGesture();
        gesture.press(0, 50, 60, true);
        assertFalse(gesture.drag(1, 80, 90, 30, 30, true).consumed());
        assertEquals(PreviewGesture.Release.NONE, gesture.release(1, 80, 90, false, 1000));
        assertEquals(new PreviewGesture.Motion(true, 0, 0), gesture.drag(0, 51, 61, 1, 1, true));
        assertEquals(new PreviewGesture.Motion(true, 10, 20), gesture.drag(0, 60, 80, 9, 19, true));
        assertEquals(new PreviewGesture.Motion(true, 5, -2), gesture.drag(0, 65, 78, 5, -2, true));
        assertEquals(PreviewGesture.Release.DRAG, gesture.release(0, -40, -10, false, 1100));
        assertEquals(-1, gesture.button());
        assertFalse(gesture.drag(0, 100, 100, 35, 22, true).consumed());
    }

    @Test void leavingThePanelWithoutMovementStillCancelsAndNeverEdits() {
        var gesture = new PreviewGesture();
        gesture.press(1, 50, 60, true);
        assertEquals(PreviewGesture.Release.DRAG, gesture.release(1, 50, 60, false, 1000));
        assertEquals(-1, gesture.button());
        assertFalse(gesture.press(0, 50, 60, false));
        assertFalse(gesture.drag(0, 80, 90, 30, 30, true).consumed());
    }

    @Test void returningToThePressPointAfterADragDoesNotTurnItIntoAClick() {
        var gesture = new PreviewGesture();
        gesture.press(0, 50, 60, true);
        gesture.drag(0, 80, 90, 30, 30, true);
        assertEquals(PreviewGesture.Release.DRAG, gesture.release(0, 50, 60, true, 1000));
    }

    @Test void missingReleaseOrWindowFocusLossCannotLeaveAPhantomDrag() {
        var gesture = new PreviewGesture();
        gesture.press(0, 50, 60, true);
        assertFalse(gesture.drag(0, 80, 90, 30, 30, false).consumed());
        assertEquals(-1, gesture.button());
        gesture.press(0, 50, 60, true);
        gesture.cancel();
        assertFalse(gesture.drag(0, 80, 90, 30, 30, true).consumed());
    }

    @Test void doubleClickResetsButADragAndRightClickNeverReset() {
        var gesture = new PreviewGesture();
        gesture.press(0, 50, 60, true);
        assertEquals(PreviewGesture.Release.CLICK, gesture.release(0, 50, 60, true, 1000));
        gesture.press(0, 51, 61, true);
        assertEquals(PreviewGesture.Release.RESET, gesture.release(0, 51, 61, true, 1200));
        assertEquals(-1, gesture.button());
        gesture.press(1, 51, 61, true);
        assertEquals(PreviewGesture.Release.CLICK, gesture.release(1, 51, 61, true, 1300));
        gesture.press(1, 51, 61, true);
        assertEquals(PreviewGesture.Release.CLICK, gesture.release(1, 51, 61, true, 1400));
        gesture.press(0, 51, 61, true);
        gesture.drag(0, 60, 70, 9, 9, true);
        assertEquals(PreviewGesture.Release.DRAG, gesture.release(0, 51, 61, true, 1500));
        gesture.press(0, 51, 61, true);
        assertEquals(PreviewGesture.Release.CLICK, gesture.release(0, 51, 61, true, 1600));
    }
}
