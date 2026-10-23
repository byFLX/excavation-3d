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
        // Small, beside the hotbar's right end, so it stays out of the view and clear of hearts and item names.
        GuiGraphics graphics = event.getGuiGraphics();
        float x = graphics.guiWidth() / 2F + HOTBAR_HALF_WIDTH + 6;
        float y = graphics.guiHeight() - 14;
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(SCALE, SCALE, 1);
        graphics.drawString(minecraft.font, line, 0, 0, 0xFFFFFF);
        graphics.pose().popPose();
    }

    private static final int HOTBAR_HALF_WIDTH = 91;
    private static final float SCALE = 0.75F;
}
