package com.byflx.shapeexcavator.registry;

import com.byflx.shapeexcavator.ShapeExcavator;
import com.byflx.shapeexcavator.item.ShapeExcavatorItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ShapeExcavator.MOD_ID);

    /** Blocks the tool mines efficiently: pickaxe and shovel blocks (data/shapeexcavator/tags/block). */
    public static final TagKey<Block> EXCAVATOR_MINEABLE = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(ShapeExcavator.MOD_ID, "excavator_mineable"));

    public static final DeferredItem<ShapeExcavatorItem> SHAPE_EXCAVATOR =
            ITEMS.register("shape_excavator", () -> new ShapeExcavatorItem(new Item.Properties()));

    private ModItems() {
    }

    public static void addToCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(SHAPE_EXCAVATOR.get());
        }
    }
}
