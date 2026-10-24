package com.byflx.excavation3d.preview;

import static org.junit.jupiter.api.Assertions.*;

import com.byflx.excavation3d.client.screen.PreviewCamera;
import org.junit.jupiter.api.Test;

class PreviewCameraTest {
    @Test void pitchNeverReachesAnEdgeOnOrTopDownViewAndYawWraps() {
        var camera = new PreviewCamera();
        camera.drag(100000, 100000);
        assertEquals(PreviewCamera.MAX_ELEVATION, camera.elevation());
        camera.drag(-200000, -200000);
        assertEquals(PreviewCamera.MIN_ELEVATION, camera.elevation());
        assertTrue(camera.yaw() >= -180 && camera.yaw() < 180);
    }

    @Test void resetRestoresBothAnglesAndZoomAfterEitherPitchLimit() {
        var camera = new PreviewCamera();
        for (int direction : new int[] {-1, 1}) {
            camera.drag(300, direction * 100000);
            camera.scroll(direction * 1000);
            assertTrue(camera.zoom() >= 0.3F && camera.zoom() <= 4);
            camera.reset();
            assertEquals(-35.0F, camera.yaw());
            assertEquals(25.0F, camera.elevation());
            assertEquals(1.0F, camera.zoom());
        }
    }
}
