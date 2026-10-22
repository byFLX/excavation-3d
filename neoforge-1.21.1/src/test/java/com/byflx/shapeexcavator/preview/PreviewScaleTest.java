package com.byflx.shapeexcavator.preview;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void smallerPanelShrinksTheScale() {
        float scale = PreviewScale.initial(W, H, 3, 3, 1);
        float smaller = PreviewScale.next(scale, 60, 40, 3, 3, 1);
        assertEquals(PreviewScale.fit(60, 40, 3, 3, 1), smaller);
        assertTrue(smaller < scale);
    }
}
