package com.byflx.excavation3d.shape;

import static com.byflx.excavation3d.shape.TestShapes.issues;
import static com.byflx.excavation3d.shape.TestShapes.valid;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.byflx.excavation3d.shape.ShapeIssue.Code;
import java.util.List;
import org.junit.jupiter.api.Test;

class ShapeLimitsTest {
    private static final ShapeLimits LOWERED = new ShapeLimits(16, 512);

    @Test
    void defaultsAreTechnicalMaximums() {
        assertEquals(new ShapeLimits(32, 4096), ShapeLimits.DEFAULT);
    }

    @Test
    void loweredLimitsApply() {
        assertEquals(List.of(ShapeIssue.of(Code.AXIS_TOO_LARGE, "width", 17, 16)),
                issues(ShapeConfig.of(new ShapeParams.Cuboid(17, 1, 1)), LOWERED));
        assertEquals(512, valid(ShapeConfig.of(new ShapeParams.Cuboid(16, 16, 2)), LOWERED).count());
        assertEquals(List.of(ShapeIssue.of(Code.SELECTION_TOO_LARGE, "selection", 768, 512)),
                issues(ShapeConfig.of(new ShapeParams.Cuboid(16, 16, 3)), LOWERED));
    }

    @Test
    void limitsOutsideTechnicalRangeAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ShapeLimits(33, 4096));
        assertThrows(IllegalArgumentException.class, () -> new ShapeLimits(32, 4097));
        assertThrows(IllegalArgumentException.class, () -> new ShapeLimits(0, 1));
        assertThrows(IllegalArgumentException.class, () -> new ShapeLimits(1, 0));
    }
}
