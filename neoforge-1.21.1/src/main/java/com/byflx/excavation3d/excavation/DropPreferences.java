package com.byflx.excavation3d.excavation;

import com.byflx.excavation3d.config.ServerConfig;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Server-thread-only session preferences; never trusted to override server policy. */
public final class DropPreferences {
    private static final Map<UUID, DropMode> CHOICES = new HashMap<>();
    private DropPreferences() { }
    public static void set(UUID player, DropMode mode) { CHOICES.put(player, mode); }
    public static DropMode effective(UUID player) {
        return ServerConfig.dropPolicy().resolve(CHOICES.getOrDefault(player, DropMode.INVENTORY));
    }
    public static void remove(UUID player) { CHOICES.remove(player); }
    public static void clear() { CHOICES.clear(); }
}
