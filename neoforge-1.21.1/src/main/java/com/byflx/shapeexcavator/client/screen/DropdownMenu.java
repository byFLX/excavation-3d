package com.byflx.shapeexcavator.client.screen;

import java.util.List;
import java.util.function.BooleanSupplier;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * A simple popup list drawn above the editor (presets, layer tools). Entries run their action on
 * click; an entry may also carry a delete action, shown as "×" at its right end. An optional header
 * area at the top is left empty for widgets the screen places there. The screen routes clicks,
 * scrolling and Escape to the open menu first.
 */
final class DropdownMenu {
    static final int ROW_HEIGHT = 12;
    private static final int DELETE_WIDTH = 12;
    private static final int BACKGROUND = 0xF0181A1F;
    private static final int BORDER = 0xFF5A5F68;
    private static final int HOVER = 0xFF2E3440;
    private static final int TEXT = 0xE0E0E0;
    private static final int DISABLED = 0x707070;
    private static final int DELETE = 0xFF7070;

    /** A row; {@code label == null} draws a separator. {@code delete} may be null. */
    record Entry(Component label, BooleanSupplier enabled, Runnable action, Runnable delete) {
        static Entry of(Component label, Runnable action) {
            return new Entry(label, () -> true, action, null);
        }

        static Entry separator() {
            return new Entry(null, () -> false, () -> {
            }, null);
        }

        boolean isEnabled() {
            return label != null && enabled.getAsBoolean();
        }
    }

    private final int x;
    private final int y;
    private final int width;
    private final int headerHeight;
    private final int visibleRows;
    private final List<Entry> entries;
    private final Component deleteTooltip;
    private int scroll;

    DropdownMenu(int x, int y, int width, int headerHeight, int maxBottom, List<Entry> entries,
            Component deleteTooltip) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.headerHeight = headerHeight;
        this.entries = List.copyOf(entries);
        this.deleteTooltip = deleteTooltip;
        int room = Math.max(1, (maxBottom - y - headerHeight - 4) / ROW_HEIGHT);
        this.visibleRows = Math.min(this.entries.size(), room);
    }

    int x() {
        return x;
    }

    int y() {
        return y;
    }

    int width() {
        return width;
    }

    private int bottom() {
        return y + headerHeight + visibleRows * ROW_HEIGHT + 4;
    }

    boolean contains(double mx, double my) {
        return mx >= x && mx < x + width && my >= y && my < bottom();
    }

    /** The entry index under the mouse, or -1. */
    private int rowAt(double mx, double my) {
        int top = y + headerHeight + 2;
        if (mx < x || mx >= x + width || my < top || my >= top + visibleRows * ROW_HEIGHT) {
            return -1;
        }
        int index = scroll + (int) ((my - top) / ROW_HEIGHT);
        return index < entries.size() ? index : -1;
    }

    private boolean onDelete(double mx) {
        return mx >= x + width - DELETE_WIDTH - 2;
    }

    void render(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        graphics.fill(x, y, x + width, bottom(), BACKGROUND);
        graphics.renderOutline(x, y, width, bottom() - y, BORDER);
        int hovered = rowAt(mouseX, mouseY);
        int top = y + headerHeight + 2;
        for (int i = 0; i < visibleRows; i++) {
            int index = scroll + i;
            if (index >= entries.size()) {
                break;
            }
            Entry entry = entries.get(index);
            int rowY = top + i * ROW_HEIGHT;
            if (entry.label() == null) {
                graphics.fill(x + 4, rowY + ROW_HEIGHT / 2, x + width - 4, rowY + ROW_HEIGHT / 2 + 1, BORDER);
                continue;
            }
            boolean enabled = entry.isEnabled();
            if (index == hovered && enabled) {
                graphics.fill(x + 1, rowY, x + width - 1, rowY + ROW_HEIGHT, HOVER);
            }
            int textRight = x + width - 4 - (entry.delete() != null ? DELETE_WIDTH : 0);
            String text = font.plainSubstrByWidth(entry.label().getString(), textRight - (x + 4));
            graphics.drawString(font, text, x + 4, rowY + 2, enabled ? TEXT : DISABLED);
            if (entry.delete() != null) {
                graphics.drawString(font, "×", x + width - DELETE_WIDTH, rowY + 2, DELETE);
                if (index == hovered && onDelete(mouseX)) {
                    graphics.renderTooltip(font, deleteTooltip, mouseX, mouseY);
                }
            }
        }
        if (entries.size() > visibleRows) {
            int track = visibleRows * ROW_HEIGHT;
            int thumb = Math.max(6, track * visibleRows / entries.size());
            int thumbY = top + (track - thumb) * scroll / (entries.size() - visibleRows);
            graphics.fill(x + width - 2, thumbY, x + width - 1, thumbY + thumb, 0xC0FFFFFF);
        }
    }

    /** Runs the clicked entry's action. Returns whether the click was inside the menu. */
    boolean click(double mx, double my) {
        if (!contains(mx, my)) {
            return false;
        }
        int index = rowAt(mx, my);
        if (index >= 0) {
            Entry entry = entries.get(index);
            if (entry.delete() != null && onDelete(mx)) {
                entry.delete().run();
            } else if (entry.isEnabled()) {
                entry.action().run();
            }
        }
        return true;
    }

    void scroll(double amount) {
        scroll = Mth.clamp(scroll - (int) Math.signum(amount), 0, Math.max(0, entries.size() - visibleRows));
    }
}
