package com.byflx.excavation3d.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

/**
 * A small vanilla button that shows a procedural icon instead of text: either a pixel pattern
 * ({@code '#'} = set) or a single glyph. No texture assets are needed. The label is the tooltip
 * and the narration; a selected button gets a gold frame and a gold icon.
 */
final class IconButton extends Button {
    private static final int ICON = 0xFFE0E0E0;
    private static final int ICON_INACTIVE = 0xFF808080;
    private static final int SELECTED = 0xFFE0B020;

    private final String[] pattern;
    private final Component glyph;
    private boolean selected;

    private IconButton(int x, int y, int width, int height, Component label, String[] pattern, Component glyph,
            OnPress onPress) {
        super(x, y, width, height, Component.empty(), onPress,
                defaultNarration -> Component.translatable("gui.narrate.button", label));
        this.pattern = pattern;
        this.glyph = glyph;
        setTooltip(Tooltip.create(label));
    }

    static IconButton pattern(int x, int y, int width, int height, Component label, OnPress onPress,
            String... rows) {
        return new IconButton(x, y, width, height, label, rows, null, onPress);
    }

    static IconButton glyph(int x, int y, int size, Component label, String glyph, OnPress onPress) {
        return new IconButton(x, y, size, size, label, null, Component.literal(glyph), onPress);
    }

    static IconButton rotate(int x, int y, int size, Component label, OnPress onPress) {
        return pattern(x, y, size, size, label, onPress,
                "..#####....", ".##...##...", "##.....##.#", "#.......###", "#......####",
                "#..........", "#..........", "##......#..", ".##....##..", "..######...", "....##.....");
    }

    static IconButton mirror(int x, int y, int size, Component label, OnPress onPress) {
        return pattern(x, y, size, size, label, onPress,
                ".....#.....", "#....#....#", "##...#...##", "###..#..###", "####.#.####",
                "###..#..###", "##...#...##", "#....#....#", ".....#.....", ".....#.....", ".....#.....");
    }

    void setSelected(boolean selected) {
        this.selected = selected;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderWidget(graphics, mouseX, mouseY, partialTick);
        int color = selected ? SELECTED : active ? ICON : ICON_INACTIVE;
        if (selected) {
            graphics.renderOutline(getX(), getY(), getWidth(), getHeight(), SELECTED);
        }
        if (glyph != null) {
            var font = Minecraft.getInstance().font;
            graphics.drawString(font, glyph, getX() + (getWidth() - font.width(glyph) + 1) / 2,
                    getY() + (getHeight() - 8) / 2, color, false);
            return;
        }
        int rows = pattern.length;
        int cols = pattern[0].length();
        int pixel = Math.max(1, Math.min((getWidth() - 6) / cols, (getHeight() - 6) / rows));
        int left = getX() + (getWidth() - pixel * cols) / 2;
        int top = getY() + (getHeight() - pixel * rows) / 2;
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                if (pattern[row].charAt(col) == '#') {
                    int px = left + col * pixel;
                    int py = top + row * pixel;
                    graphics.fill(px, py, px + pixel, py + pixel, color);
                }
            }
        }
    }
}
