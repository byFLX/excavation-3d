package com.byflx.excavation3d.editor;

import com.byflx.excavation3d.shape.ShapeConfig;
import com.byflx.excavation3d.shape.ShapeParams;
import com.byflx.excavation3d.shape.ShapeTransform;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PresetShortcutsTest {
    private static ShapeConfig shape(int width) { return ShapeConfig.of(new ShapeParams.Cuboid(width, 1, 1)); }

    @Test void slotsOneThroughFiveUseTheSuppliedSaveOrder() {
        var names = List.of("z", "echo", "Bravo", "alpha", "Alpha", "delta");
        var sorted = List.of("Alpha", "alpha", "Bravo", "delta", "echo", "z");
        assertEquals(sorted, PresetShortcuts.sorted(names));
        for (int n = 1; n <= 5; n++) assertEquals(names.get(n - 1), PresetShortcuts.nth(names, n).orElseThrow());
        assertTrue(PresetShortcuts.nth(names, 0).isEmpty());
        assertTrue(PresetShortcuts.nth(names, 7).isEmpty());
        assertTrue(PresetShortcuts.nth(List.of(), 1).isEmpty());
        assertTrue(PresetShortcuts.nth(List.of("only"), 5).isEmpty());
    }

    @Test void cycleWrapsInBothDirectionsMatchesByShapeIgnoringRevisionAndHandlesOffOrUnsaved() {
        Map<String, ShapeConfig> presets = Map.of("Zulu", shape(3), "alpha", shape(1), "Bravo", shape(2));
        var names = List.of("alpha", "Bravo", "Zulu");
        java.util.function.Function<String, Optional<ShapeConfig>> lookup = n -> Optional.ofNullable(presets.get(n));
        assertEquals("Bravo", PresetShortcuts.cycle(names, lookup, shape(1).withRevision(99), 1).orElseThrow());
        assertEquals("alpha", PresetShortcuts.cycle(names, lookup, shape(3), 1).orElseThrow());
        assertEquals("Zulu", PresetShortcuts.cycle(names, lookup, shape(1), -1).orElseThrow());
        assertEquals("Bravo", PresetShortcuts.cycle(names, lookup, shape(3), -1).orElseThrow());
        for (ShapeConfig active : new ShapeConfig[] {null, shape(4), shape(1).withTransform(new ShapeTransform(1, false))}) {
            assertEquals("alpha", PresetShortcuts.cycle(names, lookup, active, 1).orElseThrow());
            assertEquals("Zulu", PresetShortcuts.cycle(names, lookup, active, -1).orElseThrow());
        }
        assertTrue(PresetShortcuts.cycle(List.of(), lookup, null, 1).isEmpty());
        assertTrue(PresetShortcuts.cycle(names, lookup, null, 0).isEmpty());
        assertEquals("alpha", PresetShortcuts.cycle(List.of("alpha"), lookup, shape(1), -1).orElseThrow());
        assertEquals("alpha", PresetShortcuts.cycle(List.of("alpha"), lookup, shape(1), 1).orElseThrow());
    }

    @Test void duplicateShapesChooseFirstInSaveOrderUnlessConfirmedCursorMatches() {
        var names = List.of("B", "A", "C");
        assertEquals("A", PresetShortcuts.cycle(names, n -> Optional.of(shape(1)), shape(1), 1).orElseThrow());
        assertEquals("C", PresetShortcuts.cycle(names, n -> Optional.of(shape(1)), shape(1), -1).orElseThrow());
        assertEquals("C", PresetShortcuts.cycle(names, n -> Optional.of(shape(1)), shape(1), 1, "A").orElseThrow());
        assertEquals("B", PresetShortcuts.cycle(names, n -> Optional.of(shape(1)), shape(1), 1, "C").orElseThrow());
        assertEquals("A", PresetShortcuts.cycle(names, n -> Optional.of(shape(1)), shape(1), 1, "Deleted").orElseThrow());
    }
}
