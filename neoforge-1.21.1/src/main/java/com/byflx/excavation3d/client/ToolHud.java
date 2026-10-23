package com.byflx.excavation3d.client;

import com.byflx.excavation3d.data.ToolShapeData;
import com.byflx.excavation3d.editor.ToolHintState;
import com.byflx.excavation3d.preview.CellCategory;
import com.byflx.excavation3d.registry.ModTags;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/** A centred, temporary tool hint above vanilla's dynamically stacked HUD elements. */
public final class ToolHud {
    private static final float SCALE = 0.85F;
    private static ToolHintState state = new ToolHintState();
    private record HeldTool(int slot, Object item) { }
    private record ShapeState(Object config, boolean enabled) { }

    private ToolHud() { }

    public static void confirmedChange() { state.confirmedChange(); }
    public static void feedback(Component message) { state.showFeedback(message.copy()); }
    static void reset() { state = new ToolHintState(); }

    static void tick(Minecraft mc) {
        boolean eligible = mc.player != null && mc.player.getMainHandItem().is(ModTags.SHAPE_TOOLS);
        ToolShapeData data = eligible ? ModTags.shapeOf(mc.player.getMainHandItem()) : null;
        Object tool = eligible ? new HeldTool(mc.player.getInventory().selected, mc.player.getMainHandItem().getItem()) : null;
        Object shape = data == null ? null : new ShapeState(data.config().orElse(null), data.enabled());
        state.update(Util.getMillis(), eligible, tool, shape, ClientState.bypassActive(), Screen.hasShiftDown(),
                mc.screen == null && !mc.options.hideGui);
    }

    static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.screen != null
                || !mc.player.getMainHandItem().is(ModTags.SHAPE_TOOLS)) return;
        int opacity = (int) Math.round(state.opacity(Util.getMillis()) * 255);
        if (opacity < 4) return; // Minecraft treats tiny text alpha values as opaque.
        ToolShapeData data = ModTags.shapeOf(mc.player.getMainHandItem());
        Component line = ClientState.bypassActive()
                ? Component.translatable("tool.excavation3d.single_block").withStyle(ChatFormatting.YELLOW)
                : ClientState.toolSummary(data);
        if (WorldPreview.active() && Screen.hasShiftDown()) {
            line = Component.translatable("hud.excavation3d.preview",
                    WorldPreview.count(CellCategory.PRESENT), WorldPreview.count(CellCategory.AIR),
                    WorldPreview.count(CellCategory.EXCLUDED), WorldPreview.count(CellCategory.UNLOADED))
                    .withStyle(ChatFormatting.GRAY);
        }
        if (state.feedback(Util.getMillis()) instanceof Component feedback) line = feedback;
        GuiGraphics g = event.getGuiGraphics();
        List<FormattedCharSequence> lines = mc.font.split(line, (int) ((g.guiWidth() - 16) / SCALE));
        float textHeight = lines.size() * mc.font.lineHeight * SCALE;
        float y = ToolHintState.bottom(g.guiHeight(), mc.gui.leftHeight, mc.gui.rightHeight) - textHeight;
        if (y < g.guiHeight() / 2F) return; // Tiny windows: no free lower-centre space.
        g.pose().pushPose();
        g.pose().translate(g.guiWidth() / 2F, y, 0);
        g.pose().scale(SCALE, SCALE, 1);
        for (int i = 0; i < lines.size(); i++) {
            FormattedCharSequence text = lines.get(i);
            g.drawString(mc.font, text, -mc.font.width(text) / 2, i * mc.font.lineHeight,
                    (opacity << 24) | 0xFFFFFF, true);
        }
        g.pose().popPose();
    }
}
