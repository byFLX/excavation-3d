package com.byflx.excavation3d.editor;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class FavoritesLayoutTest {
    @Test void panelFitsSmallAndLargeGuiSizesAndKeepsOneWholeRowAvailable() {
        for (int width : new int[] {320, 426, 854}) {
            for (int height : new int[] {180, 240, 480}) {
                for (int rows : new int[] {0, 1, 4, 64}) {
                    var layout = FavoritesLayout.fit(width, height, 24, rows);
                    assertTrue(layout.x() >= 0);
                    assertTrue(layout.x() + layout.width() <= width);
                    assertTrue(layout.y() >= 0);
                    assertTrue(layout.y() + layout.height() <= height - 30);
                    assertTrue(layout.listBottom() - layout.listTop() >= FavoritesLayout.ROW);
                    assertTrue(layout.width() <= 220);
                }
            }
        }
    }

    @Test void headerAndOutsideClicksCannotSelectRowsAndScrollingReachesTheLastFavorite() {
        var layout = FavoritesLayout.fit(320, 240, 24, 64);
        assertEquals(-1, layout.rowAt(layout.x() + 20, layout.y() + 10, 0, 64));
        assertEquals(-1, layout.rowAt(layout.x() - 1, layout.listTop(), 0, 64));
        assertEquals(-1, layout.rowAt(layout.x() + 20, layout.listBottom(), 0, 64));
        assertEquals(0, layout.rowAt(layout.x() + 20, layout.listTop(), 0, 64));
        int maximum = layout.maxScroll(64);
        assertTrue(maximum > 0);
        assertEquals(63, layout.rowAt(layout.x() + 20, layout.listBottom() - 1, maximum, 64));
        assertEquals(-1, FavoritesLayout.fit(320, 240, 24, 0).rowAt(200, 70, 0, 0));
    }
}
