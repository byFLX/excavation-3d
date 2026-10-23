package com.byflx.excavation3d.client;

import com.byflx.excavation3d.data.ShapeCodecs;
import com.byflx.excavation3d.editor.BuiltInShapes;
import com.byflx.excavation3d.editor.PresetShortcuts;
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
        assertEquals(List.of("Alpha", "alpha", "beta", "Zulu"), repository.names());
        assertTrue(JsonParser.parseString(Files.readString(file)).getAsJsonObject().has("order"));
        assertEquals(PresetRepository.SaveResult.SAVED, repository.save("A new", shape(3)));
        assertEquals(PresetRepository.SaveResult.SAVED, repository.save("beta", shape(4)));
        assertEquals(List.of("Alpha", "alpha", "beta", "Zulu", "A new"), repository.names());
        var restored = PresetRepository.load(file);
        assertEquals(repository.names(), restored.names());
        assertEquals(shape(4), restored.find("beta").orElseThrow());
        assertTrue(restored.delete("alpha"));
        assertEquals(List.of("Alpha", "beta", "Zulu", "A new"), PresetRepository.load(file).names());
    }

    @Test void newFilesKeepSaveOrderAndShortcutsIncludeBuiltInsWithoutNameCollisions() {
        var repository = PresetRepository.load(directory.resolve("new.json"));
        repository.save("Zulu", shape(7));
        String collision = BuiltInShapes.ALL.getFirst().id();
        repository.save(collision, shape(8));
        repository.save("Alpha", shape(9));
        assertEquals(List.of("Zulu", collision, "Alpha"), repository.names());
        var names = repository.selectionNames();
        assertEquals(7, names.size());
        for (int i = 1; i <= 4; i++) {
            String name = PresetShortcuts.nth(names, i).orElseThrow();
            assertEquals(BuiltInShapes.ALL.get(i - 1).config(), repository.selection(name).orElseThrow());
        }
        assertEquals(shape(7), repository.selection(PresetShortcuts.nth(names, 5).orElseThrow()).orElseThrow());
        assertEquals(shape(8), repository.selection(names.get(5)).orElseThrow());
        assertEquals(names.getFirst(), PresetShortcuts.cycle(names, repository::selection, shape(9), 1).orElseThrow());
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
        assertEquals(List.of("Good"), repository.names());
        assertEquals(PresetRepository.SaveResult.INVALID_NAME, repository.save("  ", shape(3)));
        // A file used as a parent causes an ordinary write failure without modifying any user data.
        Path parentFile = directory.resolve("parent-file");
        Files.writeString(parentFile, "retained");
        var unwritable = PresetRepository.load(parentFile.resolve("presets.json"));
        assertEquals(PresetRepository.SaveResult.IO_ERROR, unwritable.save("New", shape(3)));
        assertTrue(unwritable.names().isEmpty());
    }
}
