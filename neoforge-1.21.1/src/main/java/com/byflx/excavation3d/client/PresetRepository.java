package com.byflx.excavation3d.client;

import com.byflx.excavation3d.data.ShapeCodecs;
import com.byflx.excavation3d.shape.ShapeConfig;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.LinkedHashMap;
import com.google.gson.JsonArray;
import com.byflx.excavation3d.editor.BuiltInShapes;
import com.byflx.excavation3d.editor.PresetShortcuts;
import java.util.ArrayList;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;

/** Personal favorites in persistent save order. Legacy files migrate once; tools remain independent. */
public final class PresetRepository {
    public static final int MAX_PRESETS = 64;
    public static final int MAX_NAME_LENGTH = 32;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static PresetRepository instance;

    public enum SaveResult {
        SAVED, INVALID_NAME, FULL, IO_ERROR
    }

    private final Path file;
    private final Map<String, ShapeConfig> presets = new LinkedHashMap<>();

    private PresetRepository(Path file) {
        this.file = file;
    }

    public static PresetRepository get() {
        if (instance == null) {
            instance = new PresetRepository(FMLPaths.CONFIGDIR.get().resolve("excavation3d-presets.json"));
            instance.read();
        }
        return instance;
    }

    /** Also used by pure persistence tests without touching the game config directory. */
    public static PresetRepository load(Path file) {
        PresetRepository repository = new PresetRepository(file);
        repository.read();
        return repository;
    }

    public List<String> selectionNames() {
        List<String> names = new ArrayList<>();
        BuiltInShapes.ALL.forEach(e -> names.add(e.id()));
        // Internal shortcut tokens avoid any collision with a built-in ID used as a favorite name.
        names().forEach(name -> names.add("@favorite/" + name));
        return List.copyOf(names);
    }

    public Optional<ShapeConfig> selection(String token) {
        return token.startsWith("@favorite/") ? find(token.substring(10))
                : BuiltInShapes.find(token).map(BuiltInShapes.Entry::config);
    }

    public List<String> names() {
        return List.copyOf(presets.keySet());
    }

    public Optional<ShapeConfig> find(String name) {
        return Optional.ofNullable(presets.get(name.trim()));
    }

    public SaveResult save(String rawName, ShapeConfig config) {
        String name = rawName == null ? "" : rawName.trim();
        if (name.isEmpty() || name.length() > MAX_NAME_LENGTH) {
            return SaveResult.INVALID_NAME;
        }
        if (!presets.containsKey(name) && presets.size() >= MAX_PRESETS) {
            return SaveResult.FULL;
        }
        Map<String, ShapeConfig> before = new LinkedHashMap<>(presets);
        presets.put(name, config.withRevision(0L));
        if (write()) return SaveResult.SAVED;
        presets.clear();
        presets.putAll(before);
        return SaveResult.IO_ERROR;
    }

    public boolean delete(String name) {
        Map<String, ShapeConfig> before = new LinkedHashMap<>(presets);
        if (presets.remove(name.trim()) == null) {
            return false;
        }
        if (write()) return true;
        presets.clear();
        presets.putAll(before);
        return false;
    }

    private void read() {
        if (!Files.isRegularFile(file)) {
            return;
        }
        try {
            JsonElement root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
            if (!root.isJsonObject() || !root.getAsJsonObject().has("presets")
                    || !root.getAsJsonObject().get("presets").isJsonObject()) {
                return;
            }
            JsonObject object = root.getAsJsonObject();
            JsonObject stored = object.getAsJsonObject("presets");
            List<String> order = new ArrayList<>();
            boolean migrate = !object.has("order") || !object.get("order").isJsonArray();
            if (!migrate) {
                for (JsonElement name : object.getAsJsonArray("order")) {
                    if (name.isJsonPrimitive() && name.getAsJsonPrimitive().isString()
                            && stored.has(name.getAsString()) && !order.contains(name.getAsString())) order.add(name.getAsString());
                }
            }
            for (String name : PresetShortcuts.sorted(List.copyOf(stored.keySet()))) {
                if (!order.contains(name)) order.add(name);
            }
            for (String storedName : order) {
                Map.Entry<String, JsonElement> entry = Map.entry(storedName, stored.get(storedName));
                String name = entry.getKey().trim();
                if (name.isEmpty() || name.length() > MAX_NAME_LENGTH || presets.size() >= MAX_PRESETS) {
                    continue;
                }
                ShapeCodecs.CONFIG_CODEC.parse(JsonOps.INSTANCE, entry.getValue()).result()
                        .ifPresent(config -> presets.put(name, config));
            }
            if (migrate) write();
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("Could not read shape presets from {}", file, e);
        }
    }

    private boolean write() {
        JsonObject entries = new JsonObject();
        for (Map.Entry<String, ShapeConfig> entry : presets.entrySet()) {
            ShapeCodecs.CONFIG_CODEC.encodeStart(JsonOps.INSTANCE, entry.getValue()).result()
                    .ifPresent(json -> entries.add(entry.getKey(), json));
        }
        JsonObject root = new JsonObject();
        root.add("presets", entries);
        JsonArray order = new JsonArray();
        presets.keySet().forEach(order::add);
        root.add("order", order);
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, new GsonBuilder().setPrettyPrinting().create().toJson(root), StandardCharsets.UTF_8);
            return true;
        } catch (IOException e) {
            LOGGER.warn("Could not write shape presets to {}", file, e);
            return false;
        }
    }
}
