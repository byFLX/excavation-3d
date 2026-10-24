package com.byflx.excavation3d.registry;

import com.byflx.excavation3d.Excavation3D;
import com.byflx.excavation3d.data.ToolShapeData;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class ModTags {
    /**
     * Tools that can carry a shape (plan §2a): pickaxes, shovels and axes by default
     * (data/excavation3d/tags/item); datapacks may add or remove tools.
     */
    public static final TagKey<Item> SHAPE_TOOLS = TagKey.create(Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath(Excavation3D.MOD_ID, "shape_tools"));

    private ModTags() {
    }

    /**
     * The shape an eligible tool carries, also an unreadable one; null for every other stack.
     * World preview needs a shape; the tooltip/HUD also show Off on eligible unconfigured tools.
     */
    public static ToolShapeData shapeOf(ItemStack stack) {
        if (!stack.is(SHAPE_TOOLS)) {
            return null;
        }
        ToolShapeData data = stack.get(ModComponents.TOOL_SHAPE.get());
        return data != null && (data.config().isPresent() || data.isDamaged()) ? data : null;
    }
}
