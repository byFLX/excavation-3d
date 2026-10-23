package com.byflx.excavation3d.preview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PreviewScaleTest {
    private static final int W = 200;
    private static final int H = 150;

    @Test
    void initialScaleIsCappedForSmallShapes() {
        assertEquals(PreviewScale.MAX_BLOCK_PX, PreviewScale.initial(W, H, 1, 1, 1));
        assertEquals(PreviewScale.MAX_BLOCK_PX, PreviewScale.initial(W, H, 3, 3, 1));
        assertEquals(PreviewScale.initial(W, H, 3, 3, 1), PreviewScale.next(0.0F, W, H, 3, 3, 1));
    }

    @Test
    void growingThatStillFitsKeepsTheScale() {
        float scale = PreviewScale.initial(W, H, 3, 3, 1);
        assertEquals(scale, PreviewScale.next(scale, W, H, 4, 3, 1));
        assertEquals(scale, PreviewScale.next(scale, W, H, 5, 3, 1));
    }

    @Test
    void growingThatStillFitsKeepsTheScaleBelowTheCap() {
        // 146 × 100 scene: 3×3×1 opens at about 13.59 px, below the cap.
        float scale = PreviewScale.initial(146, 100, 3, 3, 1);
        assertTrue(scale < PreviewScale.MAX_BLOCK_PX);
        // At that scale 4×3×1 spans about 69 px of 100, so nothing re-zooms.
        assertTrue(PreviewScale.fit(146, 100, 4, 3, 1) < scale);
        assertEquals(scale, PreviewScale.next(scale, 146, 100, 4, 3, 1));
        assertEquals(scale, PreviewScale.next(scale, 146, 100, 6, 3, 1));
    }

    @Test
    void refitHappensOnlyOnceTheShapeNoLongerFits() {
        float scale = PreviewScale.initial(146, 100, 3, 3, 1);
        // 6×3×1 spans about 92 px and still fits; 7×3×1 spans about 104 px and does not.
        assertTrue(PreviewScale.fits(scale, 146, 100, 6, 3, 1));
        assertFalse(PreviewScale.fits(scale, 146, 100, 7, 3, 1));
        float refit = PreviewScale.next(scale, 146, 100, 7, 3, 1);
        assertEquals(PreviewScale.fit(146, 100, 7, 3, 1), refit);
        assertTrue(PreviewScale.fits(refit, 146, 100, 7, 3, 1));
        assertEquals(refit, PreviewScale.next(refit, 146, 100, 8, 3, 1));
    }

    @Test
    void growingBeyondThePanelRefits() {
        float scale = PreviewScale.initial(W, H, 3, 3, 1);
        float refit = PreviewScale.next(scale, W, H, 32, 3, 1);
        assertTrue(refit < scale);
        assertEquals(PreviewScale.fit(W, H, 32, 3, 1), refit);
    }

    @Test
    void shrinkingNeverZoomsIn() {
        float large = PreviewScale.next(PreviewScale.initial(W, H, 3, 3, 1), W, H, 32, 3, 1);
        assertEquals(large, PreviewScale.next(large, W, H, 3, 3, 1));
    }

    @Test
    void growingAfterZoomingOutKeepsTheScaleWhileTheShapeFitsOnScreen() {
        float scale = PreviewScale.initial(W, H, 5, 3, 1);
        assertEquals(PreviewScale.MAX_BLOCK_PX, scale);
        // At 0.3x a 6-wide shape is shown at 4.8 px per block, far below its fit of about 15.3.
        assertTrue(PreviewScale.fit(W, H, 6, 3, 1) < scale);
        assertEquals(scale, PreviewScale.next(scale, 0.3F, W, H, 6, 3, 1));
    }

    @Test
    void growingBeyondThePanelAfterZoomingOutRefitsTheDisplayedScale() {
        float zoom = 0.3F;
        float scale = PreviewScale.initial(W, H, 5, 3, 1);
        float refit = PreviewScale.next(scale, zoom, W, H, 64, 3, 1);
        assertTrue(refit < scale);
        assertEquals(PreviewScale.fit(W, H, 64, 3, 1), refit * zoom, 1.0E-4F);
    }

    @Test
    void zoomingInNeverMakesAResizeShrinkTheScale() {
        float scale = PreviewScale.initial(W, H, 3, 3, 1);
        assertEquals(scale, PreviewScale.next(scale, 3.0F, W, H, 4, 3, 1));
        assertEquals(PreviewScale.next(scale, W, H, 32, 3, 1), PreviewScale.next(scale, 3.0F, W, H, 32, 3, 1));
    }

    @Test
    void smallerPanelShrinksTheScale() {
        float scale = PreviewScale.initial(W, H, 3, 3, 1);
        float smaller = PreviewScale.next(scale, 60, 40, 3, 3, 1);
        assertEquals(PreviewScale.fit(60, 40, 3, 3, 1), smaller);
        assertTrue(smaller < scale);
    }
}
