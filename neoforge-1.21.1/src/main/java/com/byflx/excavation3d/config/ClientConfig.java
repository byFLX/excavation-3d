package com.byflx.excavation3d.config;

import com.byflx.excavation3d.excavation.DropMode;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Client preference, independent of tools and servers. */
public final class ClientConfig {
    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.BooleanValue WORLD_PREVIEW;
    private static final ModConfigSpec.EnumValue<DropMode> DROP_MODE;
    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        WORLD_PREVIEW = builder.comment("Show the held tool's enabled shape in the world.")
                .define("worldPreview", true);
        DROP_MODE = builder.comment("Preferred excavation drop delivery, unless overridden by the server. Takes effect immediately.")
                .defineEnum("dropMode", DropMode.INVENTORY);
        SPEC = builder.build();
    }
    private ClientConfig() { }
    public static DropMode dropMode() { return SPEC.isLoaded() ? DROP_MODE.get() : DropMode.INVENTORY; }
    public static void setDropMode(DropMode mode) { DROP_MODE.set(mode); SPEC.save(); }
    public static boolean worldPreview() { return !SPEC.isLoaded() || WORLD_PREVIEW.get(); }
    public static boolean toggleWorldPreview() {
        boolean enabled = !worldPreview();
        WORLD_PREVIEW.set(enabled);
        SPEC.save();
        return enabled;
    }
}
