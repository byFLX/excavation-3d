package com.byflx.shapeexcavator.client;

import com.byflx.shapeexcavator.data.ToolShapeData;
import com.byflx.shapeexcavator.registry.ModComponents;
import com.byflx.shapeexcavator.registry.ModItems;
import com.byflx.shapeexcavator.text.ShapeTexts;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/**
 * The small tool hint above the hotbar (plan §2, §8): active shape or single-block mode, and the
 * world preview's counts with the reminder that protection is decided by the server.
 */
public final class ToolHud {
    private ToolHud() {
    }

    static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui || minecraft.screen != null) {
            return;
        }
        ItemStack tool = minecraft.player.getMainHandItem();
        if (!tool.is(ModItems.SHAPE_EXCAVATOR.get())) {
            return;
        }
        ToolShapeData data = tool.get(ModComponents.TOOL_SHAPE.get());
        Component line = ClientState.bypassActive()
                ? Component.translatable("tool.shapeexcavator.single_block").withStyle(ChatFormatting.YELLOW)
                : ShapeTexts.toolSummary(data, ClientState.limits());
        GuiGraphics graphics = event.getGuiGraphics();
        int centre = graphics.guiWidth() / 2;
        int y = graphics.guiHeight() - 72;
        graphics.drawCenteredString(minecraft.font, line, centre, y, 0xFFFFFF);
        if (WorldPreview.active()) {
            Component counts = Component.translatable("hud.shapeexcavator.preview",
                    WorldPreview.count(WorldPreview.Category.PRESENT),
                    WorldPreview.count(WorldPreview.Category.AIR),
                    WorldPreview.count(WorldPreview.Category.EXCLUDED),
                    WorldPreview.count(WorldPreview.Category.UNLOADED)).withStyle(ChatFormatting.GRAY);
            graphics.drawCenteredString(minecraft.font, counts, centre, y - 10, 0xFFFFFF);
        }
    }
}
