package com.byflx.shapeexcavator.registry;

import com.byflx.shapeexcavator.ShapeExcavator;
import com.byflx.shapeexcavator.data.ToolShapeData;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class ModTags {
    /**
     * Tools that can carry a shape (plan §2a): pickaxes, shovels and axes by default
     * (data/shapeexcavator/tags/item); datapacks may add or remove tools.
     */
    public static final TagKey<Item> SHAPE_TOOLS = TagKey.create(Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath(ShapeExcavator.MOD_ID, "shape_tools"));

    private ModTags() {
    }

    /**
     * The shape an eligible tool carries, also an unreadable one; null for every other stack. Hints
     * (tooltip, HUD, world preview) only appear when this is non-null, so tools without a shape
     * look like vanilla.
     */
    public static ToolShapeData shapeOf(ItemStack stack) {
        if (!stack.is(SHAPE_TOOLS)) {
            return null;
        }
        ToolShapeData data = stack.get(ModComponents.TOOL_SHAPE.get());
        return data != null && (data.config().isPresent() || data.isDamaged()) ? data : null;
    }
}
