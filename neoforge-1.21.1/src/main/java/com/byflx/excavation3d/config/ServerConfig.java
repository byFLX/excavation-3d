package com.byflx.excavation3d.config;

import com.byflx.excavation3d.shape.ShapeLimits;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Per-world server settings (plan §4, §10). Shape limits can only be lowered below the technical
 * maximums; budgets are the plan's suggested starting values until load tests say otherwise.
 */
public final class ServerConfig {
    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.IntValue MAX_AXIS;
    private static final ModConfigSpec.IntValue MAX_SELECTED;
    private static final ModConfigSpec.IntValue PLAYER_BUDGET;
    private static final ModConfigSpec.IntValue GLOBAL_BUDGET;

    public static final int DEFAULT_PLAYER_BUDGET = 32;
    public static final int DEFAULT_GLOBAL_BUDGET = 256;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("limits");
        MAX_AXIS = builder.comment("Largest extent of a shape along any axis, in blocks.")
                .defineInRange("maxAxis", ShapeLimits.TECHNICAL_MAX_AXIS, 1, ShapeLimits.TECHNICAL_MAX_AXIS);
        MAX_SELECTED = builder.comment("Largest number of selected blocks in one shape.")
                .defineInRange("maxSelected", ShapeLimits.TECHNICAL_MAX_SELECTED, 1, ShapeLimits.TECHNICAL_MAX_SELECTED);
        builder.pop();
        builder.push("budget");
        PLAYER_BUDGET = builder.comment("Positions checked per player and tick, including air and skipped ones.")
                .defineInRange("perPlayerPerTick", DEFAULT_PLAYER_BUDGET, 1, 4096);
        GLOBAL_BUDGET = builder.comment("Positions checked per tick across all players.")
                .defineInRange("globalPerTick", DEFAULT_GLOBAL_BUDGET, 1, 65536);
        builder.pop();
        SPEC = builder.build();
    }

    private ServerConfig() {
    }

    public static ShapeLimits limits() {
        if (!SPEC.isLoaded()) {
            return ShapeLimits.DEFAULT;
        }
        return new ShapeLimits(MAX_AXIS.get(), MAX_SELECTED.get());
    }

    public static int playerBudget() {
        return SPEC.isLoaded() ? PLAYER_BUDGET.get() : DEFAULT_PLAYER_BUDGET;
    }

    public static int globalBudget() {
        return SPEC.isLoaded() ? GLOBAL_BUDGET.get() : DEFAULT_GLOBAL_BUDGET;
    }
}
