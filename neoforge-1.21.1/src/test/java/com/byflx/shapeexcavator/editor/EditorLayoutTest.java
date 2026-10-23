package com.byflx.shapeexcavator.editor;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class EditorLayoutTest {
    @Test void columnsAndFooterStaySeparateAtGuiScalesOneThroughFour() {
        for (int windowWidth : new int[] {854, 1046, 1280, 1920}) {
            for (int windowHeight : new int[] {480, 824, 1080}) {
                for (int requestedScale = 1; requestedScale <= 4; requestedScale++) {
                    int scale = 1;
                    while (scale < requestedScale && windowWidth / (scale + 1) >= 320
                            && windowHeight / (scale + 1) >= 240) scale++;
                    int width = (int) Math.ceil((double) windowWidth / scale);
                    int height = (int) Math.ceil((double) windowHeight / scale);
                    assertFits(width, height);
                }
            }
        }
        assertFits(320, 240);
    }

    private static void assertFits(int width, int height) {
        var layout = EditorLayout.fit(width, height, 6, 9);
        assertTrue(6 + layout.leftWidth() < layout.previewLeft());
        assertTrue(layout.previewRight() - layout.previewLeft() >= 164);
        assertTrue(layout.previewRight() < width);
        assertTrue(layout.contentBottom() < layout.statusY());
        assertTrue(layout.statusY() + 18 < layout.summaryY());
        assertTrue(layout.summaryY() + 20 < layout.actionY());
        assertTrue(layout.actionY() + 20 < height);
        // Worst compact caption: 20px buttons, two caption lines, front caption and two help lines.
        int sceneTop = 22 + 2 + 20 + 3 + 18 + 2;
        int helpTop = layout.contentBottom() - 6 - 22;
        assertTrue(sceneTop + 20 + 9 + 5 < helpTop);
    }
}
