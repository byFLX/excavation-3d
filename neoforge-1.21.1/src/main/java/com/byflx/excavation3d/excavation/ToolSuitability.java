package com.byflx.excavation3d.excavation;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.level.block.state.BlockState;

/** Shared, world-independent tool intent check for excavation and its preview. */
public final class ToolSuitability {
    private ToolSuitability() {
    }

    /**
     * An explicit mining-speed or positive drops rule must match the block. Vanilla tools put
     * their mineable tag in such a rule; modded tools can use tags or direct block sets. The
     * component's default speed and a block's ability to drop without a tool prove no intent.
     * Tier/harvest restrictions remain a separate check, including earlier denies-drops rules.
     */
    public static boolean isIntendedFor(ItemStack stack, BlockState state) {
        Tool tool = stack.get(DataComponents.TOOL);
        return tool != null && MiningIntent.matches(tool.rules(), rule -> state.is(rule.blocks()),
                rule -> rule.speed().isPresent(), rule -> rule.correctForDrops().orElse(false));
    }
}
