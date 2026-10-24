package com.byflx.excavation3d;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ScaffoldTest {
    @Test
    void modIdMatchesMetadata() {
        assertEquals("excavation3d", Excavation3D.MOD_ID);
    }
}
