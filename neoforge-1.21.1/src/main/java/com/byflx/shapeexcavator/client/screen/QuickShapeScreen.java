package com.byflx.shapeexcavator.client.screen;

import com.byflx.shapeexcavator.client.ClientState;
import com.byflx.shapeexcavator.client.PresetRepository;
import com.byflx.shapeexcavator.client.QuickShapeAction;
import com.byflx.shapeexcavator.client.ShapeExcavatorClient;
import com.byflx.shapeexcavator.data.ToolShapeData;
import com.byflx.shapeexcavator.editor.QuickMenuLogic;
import com.byflx.shapeexcavator.registry.ModComponents;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** A Screen releases the mouse and blocks camera/mining input while V is held. */
public final class QuickShapeScreen extends Screen {
    private final List<QuickMenuLogic.Entry> entries;
    private final int slot;
    private final ItemStack tool;
    private final ClientState.RememberedShape last;
    private final boolean worldPreview;
    private int offset;

    public QuickShapeScreen(Player player) {
        super(Component.translatable("gui.shapeexcavator.quick.title"));
        slot = player.getInventory().selected;
        tool = player.getMainHandItem();
        last = ClientState.lastShape(player.getUUID()).orElse(null);
        ToolShapeData data = tool.get(ModComponents.TOOL_SHAPE.get());
        worldPreview = data != null && data.worldPreview();
        entries = QuickMenuLogic.entries(PresetRepository.get().names(), last != null);
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override public void onClose() {
        ShapeExcavatorClient.cancelEditorGesture();
        minecraft.setScreen(null);
    }

    private QuickMenuLogic.Layout layout() { return QuickMenuLogic.layout(width, height, entries.size(), offset); }

    @Override public void tick() {
        if (!minecraft.isWindowActive() || minecraft.player == null || minecraft.player.getInventory().selected != slot
                || minecraft.player.getMainHandItem() != tool) { onClose(); return; }
        if (!ShapeExcavatorClient.isEditorKeyDown()) {
            double mx = minecraft.mouseHandler.xpos() * width / minecraft.getWindow().getScreenWidth();
            double my = minecraft.mouseHandler.ypos() * height / minecraft.getWindow().getScreenHeight();
            int selected = layout().highlighted(mx, my);
            ShapeExcavatorClient.cancelEditorGesture();
            minecraft.setScreen(null);
            if (selected >= 0) choose(entries.get(selected));
        }
    }

    private void choose(QuickMenuLogic.Entry entry) {
        switch (entry.kind()) {
            case OFF -> QuickShapeAction.start(null, false);
            case LAST -> { if (last != null) QuickShapeAction.start(last.config(), last.worldPreview()); }
            case PRESET -> PresetRepository.get().find(entry.name()).ifPresent(config -> QuickShapeAction.start(config, worldPreview));
        }
    }

    private Component label(QuickMenuLogic.Entry entry) {
        return switch (entry.kind()) {
            case LAST -> Component.translatable("gui.shapeexcavator.quick.last");
            case OFF -> Component.translatable("gui.shapeexcavator.quick.off");
            case PRESET -> Component.literal(entry.name());
        };
    }

    @Override public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        offset = QuickMenuLogic.layout(width, height, entries.size(), offset - (int)Math.signum(sy)).offset();
        return true;
    }

    @Override public void render(GuiGraphics g, int mx, int my, float partialTick) {
        QuickMenuLogic.Layout l = layout();
        int selected = l.highlighted(mx, my);
        var hints = List.of(Component.translatable("gui.shapeexcavator.quick.release"),
                Component.translatable("gui.shapeexcavator.quick.cancel"));
        int hintLines = hints.stream().mapToInt(line -> font.split(line, l.width()).size()).sum();
        g.fill(l.x() - 4, l.y() - 16, l.x() + l.width() + 4,
                l.y() + l.rows() * QuickMenuLogic.ROW_HEIGHT + 27 + hintLines * font.lineHeight, 0xF010141C);
        g.drawString(font, title, l.x(), l.y() - 12, 0xFFFFFF);
        g.renderOutline(l.centerX() - 6, l.centerY() - 6, 12, 12, 0xFFE0E0E0);
        for (int row = 0; row < l.rows(); row++) {
            int index = l.offset() + row, y = l.y() + row * QuickMenuLogic.ROW_HEIGHT;
            if (index == selected) g.fill(l.x(), y, l.x() + l.width(), y + QuickMenuLogic.ROW_HEIGHT, 0xFF385575);
            g.drawString(font, font.plainSubstrByWidth(label(entries.get(index)).getString(), l.width() - 8), l.x() + 4, y + 6, 0xFFFFFF);
        }
        g.drawString(font, Component.translatable("gui.shapeexcavator.quick.page", l.offset() + 1,
                l.offset() + l.rows(), entries.size()), l.x(), l.y() + l.rows() * QuickMenuLogic.ROW_HEIGHT + 3, 0xAAAAAA);
        g.drawString(font, Component.translatable("gui.shapeexcavator.quick.scroll"), l.x(),
                l.y() + l.rows() * QuickMenuLogic.ROW_HEIGHT + 13, 0xAAAAAA);
        int hintY = l.y() + l.rows() * QuickMenuLogic.ROW_HEIGHT + 25;
        for (var hint : hints) {
            for (var line : font.split(hint, l.width())) {
                g.drawString(font, line, l.x(), hintY, 0xAAAAAA);
                hintY += font.lineHeight;
            }
        }
    }
}
