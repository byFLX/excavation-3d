package com.byflx.excavation3d.editor;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ToolHintStateTest {
    @Test void feedbackReplacesTheHintAndUsesItsFadeEvenWhileShiftIsHeld() {
        var hint = new ToolHintState();
        update(hint, 0, "tool", "shape", false, true);
        hint.showFeedback("No reply");
        update(hint, 100, "tool", "shape", false, true);
        assertEquals("No reply", hint.feedback(2600));
        assertEquals(1, hint.opacity(2600));
        assertEquals(0.5, hint.opacity(2850));
        assertNull(hint.feedback(3100));
        update(hint, 3200, "tool", "shape", false, false);
        assertNull(hint.feedback(3200)); // A new timer must not revive an expired failure.
        hint.confirmedChange();
        update(hint, 4000, "tool", "shape", false, false);
        assertNull(hint.feedback(4000));
        assertEquals(1, hint.opacity(4000));
    }

    @Test void hiddenFeedbackWaitsForTheGameAndDoesNotFollowAnotherTool() {
        var hint = new ToolHintState();
        update(hint, 0, "tool", "shape", false, false);
        hint.showFeedback("Preview off");
        hint.update(4000, true, "tool", "shape", false, false, false);
        hint.update(20000, true, "tool", "shape", false, false, true);
        assertEquals("Preview off", hint.feedback(22500));
        assertEquals(0.5, hint.opacity(22750));
        update(hint, 23000, "other", "shape", false, false);
        assertNull(hint.feedback(23000));
    }

    private static void update(ToolHintState hint, long now, String tool, String shape, boolean bypass, boolean shift) {
        hint.update(now, tool != null, tool, shape, bypass, shift, true);
    }

    @Test void showsForTwoAndAHalfSecondsThenFadesForHalfASecondWithoutIdleRefresh() {
        var hint = new ToolHintState();
        assertEquals(0, hint.opacity(0));
        update(hint, 100, "pickaxe", "A", false, false);
        update(hint, 2000, "pickaxe", "A", false, false);
        assertEquals(1, hint.opacity(2600));
        assertEquals(0.5, hint.opacity(2850));
        assertEquals(0, hint.opacity(3100));
        assertEquals(0, hint.opacity(9000));
    }

    @Test void toolSwitchShapeChangesOffAndBothSingleBlockTransitionsRestartTheTimer() {
        var hint = new ToolHintState();
        update(hint, 0, "slot1", "A", false, false);
        assertEquals(0, hint.opacity(3000));
        update(hint, 4000, "slot2", "A", false, false);
        assertEquals(1, hint.opacity(4000));
        update(hint, 8000, "slot2", "B", false, false);
        assertEquals(1, hint.opacity(8000));
        update(hint, 12000, "slot2", null, false, false); // Confirmed Off component sync.
        assertEquals(1, hint.opacity(12000));
        update(hint, 16000, "slot2", null, true, false); // Single-block may be rebound away from Shift.
        assertEquals(1, hint.opacity(16000));
        update(hint, 20000, "slot2", null, false, false);
        assertEquals(1, hint.opacity(20000));
        update(hint, 24000, null, null, false, false);
        assertEquals(0, hint.opacity(24000));
        update(hint, 28000, "slot2", null, false, false);
        assertEquals(1, hint.opacity(28000));
    }

    @Test void shiftHoldsDetailedHintIndefinitelyAndReleaseStartsANewLifetime() {
        var hint = new ToolHintState();
        update(hint, 0, "tool", "shape", false, false);
        update(hint, 4000, "tool", "shape", false, true);
        assertEquals(1, hint.opacity(60000));
        update(hint, 60000, "tool", "shape", false, false);
        assertEquals(1, hint.opacity(62500));
        assertEquals(0.5, hint.opacity(62750));
        assertEquals(0, hint.opacity(63000));
    }

    @Test void confirmationRefreshesEvenAnIdenticalShapeAndDefersWhileEditorOrHudIsHidden() {
        var hint = new ToolHintState();
        update(hint, 0, "tool", "shape", false, false);
        hint.confirmedChange();
        hint.update(4000, true, "tool", "shape", false, false, false);
        assertEquals(0, hint.opacity(4000));
        hint.update(20000, true, "tool", "shape", false, false, true);
        assertEquals(1, hint.opacity(22500));
        assertEquals(0.5, hint.opacity(22750));
        hint.confirmedChange();
        update(hint, 24000, "tool", "shape", false, false);
        assertEquals(1, hint.opacity(24000));
        hint.confirmedChange();
        update(hint, 25000, null, null, false, false);
        assertEquals(0, hint.opacity(25000));
    }

    @Test void hintSitsDirectlyAboveTheHeartsAndBelowItemNamesAndTheActionBar() {
        for (int height : new int[] {240, 360, 1080}) {
            for (int left : new int[] {39, 49, 59, 100}) {
                for (int right : new int[] {39, 59, 90}) {
                    int bottom = ToolHintState.bottom(height, left, right);
                    int stack = Math.max(39, Math.max(left, right));
                    assertEquals(height - stack - 2, bottom);
                    assertTrue(bottom <= height - stack, "never overlaps hearts, food or armor");
                }
            }
        }
        // Survival default stack: the hint ends 41 px above the bottom, below vanilla's item name (59) and action bar (68).
        assertTrue(1080 - ToolHintState.bottom(1080, 39, 39) < 59);
    }
}
