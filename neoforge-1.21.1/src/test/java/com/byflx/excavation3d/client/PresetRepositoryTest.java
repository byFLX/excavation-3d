package com.byflx.excavation3d.client;

import com.byflx.excavation3d.data.ShapeCodecs;
import com.byflx.excavation3d.editor.BuiltInShapes;
import com.byflx.excavation3d.editor.PresetShortcuts;
import com.byflx.excavation3d.editor.QuickMenuLogic;
import com.byflx.excavation3d.shape.ShapeConfig;
import com.byflx.excavation3d.shape.ShapeParams;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.io.CleanupMode;
import static org.junit.jupiter.api.Assertions.*;

class PresetRepositoryTest {
    @TempDir(cleanup = CleanupMode.NEVER) Path directory;
    private static ShapeConfig shape(int width) { return ShapeConfig.of(new ShapeParams.Cuboid(width, 1, 1)); }
    private static List<String> defaults() { return List.of("Tunnel 1×2", "Tunnel 3×3", "Floor 5×5", "Stairs"); }
    private static List<String> withDefaults(String... names) {
        var result = new java.util.ArrayList<>(defaults());
        result.addAll(List.of(names));
        return List.copyOf(result);
    }

    @Test void readingSanitizesNamesAndSkipsThoseMadeEmptyWithoutChangingSaveOrder() throws Exception {
        Path file = directory.resolve("control-names.json");
        JsonObject entries = new JsonObject();
        var order = new com.google.gson.JsonArray();
        for (String name : List.of("Zulu", " a\nb\rc ", "\u0000\t\n\r\u0085\u2028\u2029", "A\u0001\u007f\u009f\u2028\u2029B", "Alpha")) {
            entries.add(name, ShapeCodecs.CONFIG_CODEC.encodeStart(JsonOps.INSTANCE, shape(2)).getOrThrow());
            order.add(name);
        }
        JsonObject root = new JsonObject();
        root.addProperty("defaultsSeeded", true);
        root.add("presets", entries);
        root.add("order", order);
        Files.writeString(file, root.toString());
        var repository = PresetRepository.load(file);
        assertEquals(List.of("Zulu", "abc", "AB", "Alpha"), repository.names());
        assertEquals(shape(2), repository.find("abc").orElseThrow());
        assertEquals(PresetRepository.SaveResult.INVALID_NAME, repository.save("\n\u2028\u0001", shape(3)));
        assertEquals(PresetRepository.SaveResult.SAVED, repository.save("new\n\t\u2028name", shape(3)));
        assertEquals(List.of("Zulu", "abc", "AB", "Alpha", "newname"), PresetRepository.load(file).names());
        assertEquals(shape(3), repository.find("newname").orElseThrow());
    }

    @Test void missingFileSeedsAndPersistsDefaultsExactlyOnce() throws Exception {
        Path file = directory.resolve("config/presets.json");
        assertFalse(Files.exists(file));
        var repository = PresetRepository.load(file);
        assertEquals(defaults(), repository.names());
        var stored = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        assertTrue(stored.get("defaultsSeeded").getAsBoolean());
        assertEquals(defaults(), stored.getAsJsonArray("order").asList().stream().map(e -> e.getAsString()).toList());
        var restored = PresetRepository.load(file);
        assertEquals(defaults(), restored.names());
        for (var seed : BuiltInShapes.ALL) assertEquals(seed.config(), restored.find(seed.name()).orElseThrow());
    }

    @Test void roundEightOrderIsPreservedAfterDefaultsForMissingOrFalseMarker() throws Exception {
        for (boolean explicitFalse : List.of(false, true)) {
            Path file = directory.resolve("round8-" + explicitFalse + ".json");
            JsonObject entries = new JsonObject();
            entries.add("Alpha", ShapeCodecs.CONFIG_CODEC.encodeStart(JsonOps.INSTANCE, shape(8)).getOrThrow());
            entries.add("Zulu", ShapeCodecs.CONFIG_CODEC.encodeStart(JsonOps.INSTANCE, shape(9)).getOrThrow());
            JsonObject root = new JsonObject();
            root.add("presets", entries);
            root.add("order", JsonParser.parseString("[\"Zulu\",\"Alpha\"]"));
            if (explicitFalse) root.addProperty("defaultsSeeded", false);
            Files.writeString(file, root.toString());
            var repository = PresetRepository.load(file);
            assertEquals(withDefaults("Zulu", "Alpha"), repository.names());
            assertEquals(shape(9), repository.find("Zulu").orElseThrow());
            assertEquals(shape(8), repository.find("Alpha").orElseThrow());
            assertTrue(JsonParser.parseString(Files.readString(file)).getAsJsonObject().get("defaultsSeeded").getAsBoolean());
            assertEquals(repository.names(), PresetRepository.load(file).names());
        }
    }

    @Test void seedNameCollisionKeepsUserConfigurationAndItsExistingSlot() throws Exception {
        Path file = directory.resolve("collision.json");
        JsonObject entries = new JsonObject();
        for (String name : List.of("Zulu", "Tunnel 1×2", "Alpha")) {
            entries.add(name, ShapeCodecs.CONFIG_CODEC.encodeStart(JsonOps.INSTANCE, shape(9)).getOrThrow());
        }
        JsonObject root = new JsonObject();
        root.add("presets", entries);
        root.add("order", JsonParser.parseString("[\"Zulu\",\"Tunnel 1×2\",\"Alpha\"]"));
        Files.writeString(file, root.toString());
        var repository = PresetRepository.load(file);
        assertEquals(List.of("Tunnel 3×3", "Floor 5×5", "Stairs", "Zulu", "Tunnel 1×2", "Alpha"), repository.names());
        assertEquals(shape(9), repository.find("Tunnel 1×2").orElseThrow());
        var restored = PresetRepository.load(file);
        assertEquals(repository.names(), restored.names());
        assertEquals(shape(9), restored.selection("Tunnel 1×2").orElseThrow());
    }

    @Test void deletingFormerBuiltInsUsesTwoClicksAndNeverReseedsEvenWhenEmpty() throws Exception {
        Path file = directory.resolve("delete.json");
        var repository = PresetRepository.load(file);
        var state = new QuickMenuLogic.FavoriteDelete();
        for (String name : defaults()) {
            var entry = QuickMenuLogic.entries(repository.names()).get(1);
            assertEquals(name, entry.name());
            assertFalse(state.rightClick(entry));
            assertTrue(repository.find(name).isPresent());
            assertTrue(state.rightClick(entry));
            assertTrue(repository.delete(name));
            repository = PresetRepository.load(file);
            assertTrue(repository.find(name).isEmpty());
        }
        assertTrue(repository.names().isEmpty());
        assertTrue(repository.selectionNames().isEmpty());
        assertTrue(PresetShortcuts.nth(repository.selectionNames(), 1).isEmpty());
        assertTrue(PresetShortcuts.cycle(repository.selectionNames(), repository::selection, null, 1).isEmpty());
        assertEquals(QuickMenuLogic.Kind.HINT, QuickMenuLogic.entries(repository.names()).get(1).kind());
        assertTrue(JsonParser.parseString(Files.readString(file)).getAsJsonObject().get("defaultsSeeded").getAsBoolean());
        assertEquals(PresetRepository.SaveResult.SAVED, repository.save("Only", shape(7)));
        assertEquals(List.of("Only"), PresetRepository.load(file).names());
    }

    @Test void seedingAFullFileDoesNotLoseExistingFavoritesOnReload() throws Exception {
        Path file = directory.resolve("full.json");
        JsonObject entries = new JsonObject();
        for (int i = 0; i < PresetRepository.MAX_PRESETS; i++) {
            entries.add("User " + i, ShapeCodecs.CONFIG_CODEC.encodeStart(JsonOps.INSTANCE, shape(8)).getOrThrow());
        }
        JsonObject root = new JsonObject();
        root.add("presets", entries);
        Files.writeString(file, root.toString());
        var repository = PresetRepository.load(file);
        assertEquals(PresetRepository.MAX_PRESETS + 4, repository.names().size());
        var restored = PresetRepository.load(file);
        assertEquals(repository.names(), restored.names());
        for (String name : entries.keySet()) assertEquals(shape(8), restored.find(name).orElseThrow());
        assertEquals(PresetRepository.SaveResult.FULL, restored.save("Extra", shape(7)));
        assertEquals(PresetRepository.SaveResult.SAVED, restored.save("User 0", shape(7)));
    }

    @Test void legacyFileMigratesAlphabeticallyOnceThenAppendsAndRetainsOverwriteSlot() throws Exception {
        Path file = directory.resolve("presets.json");
        JsonObject entries = new JsonObject();
        for (String name : List.of("Zulu", "beta", "Alpha", "alpha")) {
            entries.add(name, ShapeCodecs.CONFIG_CODEC.encodeStart(JsonOps.INSTANCE, shape(2)).getOrThrow());
        }
        JsonObject root = new JsonObject();
        root.add("presets", entries);
        Files.writeString(file, root.toString());
        var repository = PresetRepository.load(file);
        assertEquals(withDefaults("Alpha", "alpha", "beta", "Zulu"), repository.names());
        assertTrue(JsonParser.parseString(Files.readString(file)).getAsJsonObject().has("order"));
        assertEquals(PresetRepository.SaveResult.SAVED, repository.save("A new", shape(3)));
        assertEquals(PresetRepository.SaveResult.SAVED, repository.save("beta", shape(4)));
        assertEquals(withDefaults("Alpha", "alpha", "beta", "Zulu", "A new"), repository.names());
        var restored = PresetRepository.load(file);
        assertEquals(repository.names(), restored.names());
        assertEquals(shape(4), restored.find("beta").orElseThrow());
        assertTrue(restored.delete("alpha"));
        assertEquals(withDefaults("Alpha", "beta", "Zulu", "A new"), PresetRepository.load(file).names());
    }

    @Test void wheelAndShortcutsUsePlainFavoriteSaveOrderIncludingLiteralTokenNames() {
        var repository = PresetRepository.load(directory.resolve("new.json"));
        repository.save("Zulu", shape(7));
        repository.save("@favorite/Alpha", shape(8));
        repository.save("Alpha", shape(9));
        assertEquals(withDefaults("Zulu", "@favorite/Alpha", "Alpha"), repository.names());
        var names = repository.selectionNames();
        assertEquals(repository.names(), names);
        assertEquals(names, QuickMenuLogic.entries(names).stream().skip(1).map(QuickMenuLogic.Entry::name).toList());
        for (int i = 1; i <= 4; i++) {
            String name = PresetShortcuts.nth(names, i).orElseThrow();
            assertEquals(BuiltInShapes.ALL.get(i - 1).config(), repository.selection(name).orElseThrow());
        }
        assertEquals(shape(7), repository.selection(PresetShortcuts.nth(names, 5).orElseThrow()).orElseThrow());
        assertEquals(shape(8), repository.selection(names.get(5)).orElseThrow());
        assertEquals(names.getFirst(), PresetShortcuts.cycle(names, repository::selection, shape(9), 1).orElseThrow());
        assertEquals("Alpha", PresetShortcuts.cycle(names, repository::selection, null, -1).orElseThrow());
        assertTrue(repository.delete("Tunnel 1×2"));
        assertEquals("Tunnel 3×3", PresetShortcuts.nth(repository.selectionNames(), 1).orElseThrow());
        assertEquals("@favorite/Alpha", PresetShortcuts.nth(repository.selectionNames(), 5).orElseThrow());
    }

    @Test void invalidEntriesDoNotDiscardValidFavoritesAndFailedWritesRollBackOrder() throws Exception {
        Path file = directory.resolve("partial.json");
        JsonObject entries = new JsonObject();
        entries.add("Good", ShapeCodecs.CONFIG_CODEC.encodeStart(JsonOps.INSTANCE, shape(2)).getOrThrow());
        entries.addProperty("Broken", "invalid");
        JsonObject root = new JsonObject();
        root.add("presets", entries);
        Files.writeString(file, root.toString());
        var repository = PresetRepository.load(file);
        assertEquals(withDefaults("Good"), repository.names());
        assertEquals(PresetRepository.SaveResult.INVALID_NAME, repository.save("  ", shape(3)));
        // A file used as a parent causes an ordinary write failure without modifying any user data.
        Path parentFile = directory.resolve("parent-file");
        Files.writeString(parentFile, "retained");
        var unwritable = PresetRepository.load(parentFile.resolve("presets.json"));
        assertEquals(PresetRepository.SaveResult.IO_ERROR, unwritable.save("New", shape(3)));
        assertEquals(defaults(), unwritable.names());
        assertFalse(unwritable.delete("Stairs"));
        assertEquals(defaults(), unwritable.names());
    }
}
