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
    private static final String[] OFF = {"#####", "#...#", "#.#.#", "#...#", "#####"};

    static String[] pattern(ShapeType type) { return PATTERNS.get(type); }

    static void draw(GuiGraphics g, ShapeType type, int x, int y, int pixel, int color) {
        String[] rows = type == null ? OFF : pattern(type);
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
