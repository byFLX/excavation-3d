package com.byflx.excavation3d.editor;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PanelScrollTest {
    @Test void draggingKeepsTheGrabOffsetAndCanReachBothEndsOutsideTheTrack() {
        var bar = new PanelScroll(22, 169, 140);
        int grab = 5;
        for (int scroll : new int[] {0, 70, 140}) {
            assertEquals(scroll, bar.dragged(bar.thumbY(scroll) + grab, grab), 2);
        }
        assertEquals(0, bar.dragged(-100, grab));
        assertEquals(140, bar.dragged(500, grab));
        assertEquals(22, bar.thumbY(0));
        assertEquals(169, bar.thumbY(140) + bar.thumbHeight());
    }
}
