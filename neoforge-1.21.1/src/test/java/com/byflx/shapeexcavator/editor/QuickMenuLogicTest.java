package com.byflx.shapeexcavator.editor;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class QuickMenuLogicTest {
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

    @Test void entryUnderMouseIsHighlightedAndTheCentreAndOutsideCancel() {
        var layout = QuickMenuLogic.layout(640, 360, 4, 0);
        assertEquals(-1, layout.highlighted(320, 180));
        assertEquals(0, layout.highlighted(layout.x(), layout.y()));
        assertEquals(3, layout.highlighted(layout.x() + 10, layout.y() + 79));
        assertEquals(-1, layout.highlighted(layout.x() + 10, layout.y() + 80));
        assertEquals(-1, layout.highlighted(layout.x() + layout.width(), layout.y()));
    }

    @Test void everyPresetIsReachableInASmallWindowAndScrollIsBounded() {
        for (int offset = 0; offset < 66; offset++) {
            var layout = QuickMenuLogic.layout(320, 240, 66, offset);
            assertTrue(layout.y() >= 0);
            assertTrue(layout.y() + layout.rows() * 20 <= 240);
            assertEquals(layout.offset(), layout.highlighted(layout.x() + 2, layout.y() + 2));
        }
        var lastPage = QuickMenuLogic.layout(320, 240, 66, 1000);
        assertEquals(65, lastPage.highlighted(lastPage.x() + 2, lastPage.y() + (lastPage.rows() - 1) * 20 + 2));
        assertEquals(0, QuickMenuLogic.layout(320, 240, 66, -1).offset());
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
