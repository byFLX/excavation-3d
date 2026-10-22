package com.byflx.shapeexcavator.client;

import com.byflx.shapeexcavator.data.ShapeCodecs;
import com.byflx.shapeexcavator.shape.ShapeConfig;
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
import java.util.TreeMap;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;

/**
 * Personal named presets, stored on the client in {@code config/shapeexcavator-presets.json}
 * (plan §9). A preset is only a draft source: loading one goes through the editor, which checks it
 * against the server's limits again. Tools never depend on this file. Broken entries are skipped,
 * not fatal.
 */
public final class PresetRepository {
    public static final int MAX_PRESETS = 64;
    public static final int MAX_NAME_LENGTH = 32;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static PresetRepository instance;

    public enum SaveResult {
        SAVED, INVALID_NAME, FULL, IO_ERROR
    }

    private final Path file;
    private final Map<String, ShapeConfig> presets = new TreeMap<>();

    private PresetRepository(Path file) {
        this.file = file;
    }

    public static PresetRepository get() {
        if (instance == null) {
            instance = new PresetRepository(FMLPaths.CONFIGDIR.get().resolve("shapeexcavator-presets.json"));
            instance.read();
        }
        return instance;
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
        presets.put(name, config.withRevision(0L));
        return write() ? SaveResult.SAVED : SaveResult.IO_ERROR;
    }

    public boolean delete(String name) {
        if (presets.remove(name.trim()) == null) {
            return false;
        }
        return write();
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
            for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject().getAsJsonObject("presets").entrySet()) {
                String name = entry.getKey().trim();
                if (name.isEmpty() || name.length() > MAX_NAME_LENGTH || presets.size() >= MAX_PRESETS) {
                    continue;
                }
                ShapeCodecs.CONFIG_CODEC.parse(JsonOps.INSTANCE, entry.getValue()).result()
                        .ifPresent(config -> presets.put(name, config));
            }
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
