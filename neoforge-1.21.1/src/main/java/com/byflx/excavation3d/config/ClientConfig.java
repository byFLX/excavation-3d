package com.byflx.excavation3d.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Client preference, independent of tools and servers. */
public final class ClientConfig {
    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.BooleanValue WORLD_PREVIEW;
    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        WORLD_PREVIEW = builder.comment("Show the held tool's enabled shape in the world.")
                .define("worldPreview", true);
        SPEC = builder.build();
    }
    private ClientConfig() { }
    public static boolean worldPreview() { return !SPEC.isLoaded() || WORLD_PREVIEW.get(); }
    public static boolean toggleWorldPreview() {
        boolean enabled = !worldPreview();
        WORLD_PREVIEW.set(enabled);
        SPEC.save();
        return enabled;
    }
}
