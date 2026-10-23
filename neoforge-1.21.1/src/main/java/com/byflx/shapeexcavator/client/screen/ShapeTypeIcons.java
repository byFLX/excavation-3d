package com.byflx.shapeexcavator.client.screen;

import com.byflx.shapeexcavator.shape.ShapeType;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;

/** Shared editor and quick-wheel glyphs: front views and side views for stair-like shapes. */
final class ShapeTypeIcons {
    private static final Map<ShapeType, String[]> PATTERNS = Map.of(
            ShapeType.CUBOID, new String[] {"#####", "#####", "#####", "#####", "#####"},
            ShapeType.PLUS, new String[] {"..#..", "..#..", "#####", "..#..", "..#.."},
            ShapeType.STAIR, new String[] {"#....", "##...", "###..", "####.", "#####"},
            ShapeType.CORRIDOR, new String[] {"...##", "..###", ".###.", "###..", "##..."},
            ShapeType.CUSTOM, new String[] {"#.#.#", ".#.#.", "#.#.#", ".#.#.", "#.#.#"});
    private static final String[] OFF = {"......#", ".#####.", ".#..##.", ".#.#.#.", ".##..#.", ".#####.", "#......"};
    private static final String[] LAST = {"..###..", ".#...#.", "#.....#", "#......", "#...#..", ".#..##.", "..#####"};
    private static final String[] SAVE = {"#######", "#.#.#.#", "#.#.#.#", "#.....#", "#.###.#", "#.#.#.#", "#######"};

    static String[] pattern(ShapeType type) { return PATTERNS.get(type); }

    static void draw(GuiGraphics g, ShapeType type, int x, int y, int pixel, int color) {
        drawPattern(g, type == null ? OFF : pattern(type), x, y, pixel, color);
    }

    static void drawOff(GuiGraphics g, int x, int y, int pixel, int color) {
        drawPattern(g, OFF, x, y, pixel, color);
    }

    static void drawLast(GuiGraphics g, int x, int y, int pixel, int color) {
        drawPattern(g, LAST, x, y, pixel, color);
    }

    static void drawSave(GuiGraphics g, int x, int y, int pixel, int color) {
        drawPattern(g, SAVE, x, y, pixel, color);
    }

    private static void drawPattern(GuiGraphics g, String[] rows, int x, int y, int pixel, int color) {
        for (int row = 0; row < rows.length; row++) {
            for (int col = 0; col < rows[row].length(); col++) {
                if (rows[row].charAt(col) == '#') {
                    g.fill(x + col * pixel, y + row * pixel,
                            x + (col + 1) * pixel, y + (row + 1) * pixel, color);
                }
            }
        }
    }

    private ShapeTypeIcons() { }
}
