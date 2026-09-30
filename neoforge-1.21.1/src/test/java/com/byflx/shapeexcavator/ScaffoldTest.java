package com.byflx.shapeexcavator;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ScaffoldTest {
    @Test
    void modIdMatchesMetadata() {
        assertEquals("shapeexcavator", ShapeExcavator.MOD_ID);
    }
}
