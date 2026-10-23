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

    @Test void slotsOneThroughFiveUseTheSameStableAlphabeticalOrderAsTheWheel() {
        var names = List.of("z", "echo", "Bravo", "alpha", "Alpha", "delta");
        var sorted = List.of("Alpha", "alpha", "Bravo", "delta", "echo", "z");
        assertEquals(sorted, PresetShortcuts.sorted(names));
        for (int n = 1; n <= 5; n++) assertEquals(sorted.get(n - 1), PresetShortcuts.nth(names, n).orElseThrow());
        assertTrue(PresetShortcuts.nth(names, 0).isEmpty());
        assertTrue(PresetShortcuts.nth(names, 7).isEmpty());
        assertTrue(PresetShortcuts.nth(List.of(), 1).isEmpty());
        assertTrue(PresetShortcuts.nth(List.of("only"), 5).isEmpty());
    }

    @Test void cycleWrapsInBothDirectionsMatchesByShapeIgnoringRevisionAndHandlesOffOrUnsaved() {
        Map<String, ShapeConfig> presets = Map.of("Zulu", shape(3), "alpha", shape(1), "Bravo", shape(2));
        var names = List.copyOf(presets.keySet());
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

    @Test void duplicateShapesChooseTheFirstAlphabeticalMatchDeterministically() {
        var names = List.of("B", "A", "C");
        assertEquals("B", PresetShortcuts.cycle(names, n -> Optional.of(shape(1)), shape(1), 1).orElseThrow());
        assertEquals("C", PresetShortcuts.cycle(names, n -> Optional.of(shape(1)), shape(1), -1).orElseThrow());
        assertEquals("C", PresetShortcuts.cycle(names, n -> Optional.of(shape(1)), shape(1), 1, "B").orElseThrow());
        assertEquals("A", PresetShortcuts.cycle(names, n -> Optional.of(shape(1)), shape(1), -1, "B").orElseThrow());
        assertEquals("A", PresetShortcuts.cycle(names, n -> Optional.of(shape(1)), shape(1), 1, "C").orElseThrow());
        assertEquals("B", PresetShortcuts.cycle(names, n -> Optional.of(shape(1)), shape(1), 1, "Deleted").orElseThrow());
        assertEquals("B", PresetShortcuts.cycle(names, n -> Optional.of(shape(n.equals("C") ? 2 : 1)),
                shape(1), 1, "C").orElseThrow());
    }

    @Test void toggleUsesOnlyConfirmedLastShapeAndRetainsItsPreviewFlagAcrossRestore() {
        record Remembered(ShapeConfig config, boolean preview) { }
        var memory = new QuickMenuLogic.LastShape<Remembered>();
        UUID player = UUID.randomUUID();
        var previous = new Remembered(shape(4), true);
        assertEquals(PresetShortcuts.Toggle.NONE, PresetShortcuts.toggle(false, memory.find(player).isPresent()));
        assertEquals(PresetShortcuts.Toggle.OFF, PresetShortcuts.toggle(true, false));
        memory.expectRemoval(1, player, previous);
        assertEquals(PresetShortcuts.Toggle.NONE, PresetShortcuts.toggle(false, memory.find(player).isPresent()));
        memory.onResult(1, false);
        assertTrue(memory.find(player).isEmpty());
        memory.expectRemoval(2, player, previous);
        memory.onResult(99, true);
        assertTrue(memory.find(player).isEmpty());
        memory.onResult(2, true);
        assertEquals(PresetShortcuts.Toggle.RESTORE, PresetShortcuts.toggle(false, memory.find(player).isPresent()));
        assertEquals(previous, memory.find(player).orElseThrow());
        assertEquals(PresetShortcuts.Toggle.OFF, PresetShortcuts.toggle(true, memory.find(player).isPresent()));
        memory.expectRemoval(3, player, null); // Off on an empty tool cannot overwrite memory.
        memory.onResult(3, true);
        assertEquals(previous, memory.find(player).orElseThrow());
        assertTrue(memory.find(UUID.randomUUID()).isEmpty());
    }
}
