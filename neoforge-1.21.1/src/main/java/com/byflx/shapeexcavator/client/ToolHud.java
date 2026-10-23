package com.byflx.shapeexcavator.client;

import com.byflx.shapeexcavator.data.ToolShapeData;
import com.byflx.shapeexcavator.preview.CellCategory;
import com.byflx.shapeexcavator.registry.ModTags;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/**
 * One tool hint above the hotbar: active shape or single-block mode. Holding Shift replaces it
 * with detailed world-preview counts and the reminder that protection is decided by the server.
 */
public final class ToolHud {
    private ToolHud() {
    }

    static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui || minecraft.screen != null
                || QuickShapeAction.showingFeedback()) {
            return;
        }
        // Eligible tools also show Off, so the quick menu's normal-mining state is visible.
        ToolShapeData data = ModTags.shapeOf(minecraft.player.getMainHandItem());
        if (!minecraft.player.getMainHandItem().is(ModTags.SHAPE_TOOLS)) {
            return;
        }
        Component line = ClientState.bypassActive()
                ? Component.translatable("tool.shapeexcavator.single_block").withStyle(ChatFormatting.YELLOW)
                : ClientState.toolSummary(data);
        // Details replace the tool line only while Shift is physically held.
        if (WorldPreview.active() && Screen.hasShiftDown()) {
            line = Component.translatable("hud.shapeexcavator.preview",
                    WorldPreview.count(CellCategory.PRESENT),
                    WorldPreview.count(CellCategory.AIR),
                    WorldPreview.count(CellCategory.EXCLUDED),
                    WorldPreview.count(CellCategory.UNLOADED)).withStyle(ChatFormatting.GRAY);
        }
        GuiGraphics graphics = event.getGuiGraphics();
        int centre = graphics.guiWidth() / 2;
        int y = graphics.guiHeight() - 72;
        graphics.drawCenteredString(minecraft.font, line, centre, y, 0xFFFFFF);
    }
}
