package com.byflx.excavation3d.client.screen;

import com.byflx.excavation3d.editor.FavoritesLayout;
import com.byflx.excavation3d.editor.FavoritesOverview;
import com.byflx.excavation3d.editor.PanelScroll;
import com.byflx.excavation3d.editor.QuickMenuLogic;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Favorite overlay; the owning screen supplies the save widgets and repository actions. */
final class FavoritesPanel {
    private final int screenWidth, screenHeight, top;
    private final FavoritesOverview state;
    private final Consumer<FavoritesOverview.Row> select;
    private final Consumer<String> delete;
    private List<FavoritesOverview.Row> rows = List.of();
    private FavoritesLayout layout;
    private int scroll;
    private boolean dragging;
    private double grab;

    FavoritesPanel(int screenWidth, int screenHeight, int top, FavoritesOverview state,
            Consumer<FavoritesOverview.Row> select, Consumer<String> delete) {
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
        this.top = top;
        this.state = state;
        this.select = select;
        this.delete = delete;
        setRows(List.of());
    }

    void setRows(List<FavoritesOverview.Row> rows) {
        this.rows = List.copyOf(rows);
        layout = FavoritesLayout.fit(screenWidth, screenHeight, top, rows.size());
        scroll = Math.clamp(scroll, 0, layout.maxScroll(rows.size()));
    }

    int x() { return layout.x(); }
    int y() { return layout.y(); }
    int width() { return layout.width(); }
    int scrollOffset() { return scroll; }
    void restoreScroll(int value) { scroll = Math.clamp(value, 0, layout.maxScroll(rows.size())); }
    boolean contains(double mx, double my) { return layout.contains(mx, my); }
    private int trackX() { return x() + width() - 7; }
    private int deleteX() { return trackX() - 17; }
    private PanelScroll scrollbar() {
        return new PanelScroll(layout.listTop(), layout.listBottom(), layout.maxScroll(rows.size()));
    }

    boolean click(double mx, double my, long now) {
        if (!contains(mx, my)) return false;
        if (layout.maxScroll(rows.size()) > 0 && mx >= trackX() && my >= layout.listTop()
                && my < layout.listBottom()) {
            PanelScroll bar = scrollbar();
            int thumbY = bar.thumbY(scroll);
            grab = my >= thumbY && my < thumbY + bar.thumbHeight() ? my - thumbY : bar.thumbHeight() / 2.0;
            dragging = true;
            scroll = bar.dragged(my, grab);
            state.cancelDelete();
            return true;
        }
        int row = layout.rowAt(mx, my, scroll, rows.size());
        if (row >= 0 && mx < trackX()) {
            FavoritesOverview.Row entry = rows.get(row);
            if (mx >= deleteX()) {
                if (state.deleteClick(entry.name(), now)) delete.accept(entry.name());
            } else {
                select.accept(entry);
            }
        }
        return true;
    }

    boolean drag(double my) {
        if (!dragging) return false;
        scroll = scrollbar().dragged(my, grab);
        return true;
    }

    void release() { dragging = false; }

    void scroll(double amount) {
        if (amount == 0) return;
        state.cancelDelete();
        scroll = Math.clamp(scroll - (int) Math.signum(amount) * FavoritesLayout.ROW,
                0, layout.maxScroll(rows.size()));
    }

    void render(GuiGraphics g, Font font, int mx, int my, Component status, int statusColor, long now) {
        int right = x() + width(), bottom = y() + layout.height();
        g.fill(x(), y(), right, bottom, 0xF0181A1F);
        g.renderOutline(x(), y(), width(), layout.height(), 0xFF5A5F68);
        Component tooltip = null;
        String statusText = QuickMenuLogic.fitText(status.getString(), width() - 8, font::width);
        g.drawString(font, statusText, x() + 4, y() + 26, statusColor);
        if (contains(mx, my) && my >= y() + 24 && my < layout.listTop()
                && !statusText.equals(status.getString())) tooltip = status;
        g.fill(x() + 4, layout.listTop() - 2, right - 4, layout.listTop() - 1, 0xFF5A5F68);

        int hovered = layout.rowAt(mx, my, scroll, rows.size());
        String pending = state.pendingDelete(now);
        g.enableScissor(x() + 1, layout.listTop(), right - 1, layout.listBottom());
        if (rows.isEmpty()) {
            int lineY = layout.listTop() + 3;
            for (var line : font.split(Component.translatable("gui.excavation3d.favorite.empty"), width() - 12)) {
                g.drawString(font, line, x() + 6, lineY, 0x909090);
                lineY += font.lineHeight;
            }
        }
        for (int i = 0; i < rows.size(); i++) {
            int rowY = layout.listTop() + i * FavoritesLayout.ROW - scroll;
            if (rowY + FavoritesLayout.ROW <= layout.listTop() || rowY >= layout.listBottom()) continue;
            FavoritesOverview.Row row = rows.get(i);
            if (i == hovered && mx < trackX()) g.fill(x() + 1, rowY, trackX(), rowY + FavoritesLayout.ROW, 0xFF2E3440);
            if (row.active()) g.fill(x() + 3, rowY + 9, x() + 6, rowY + 12, 0xFFFFD56B);
            ShapeTypeIcons.draw(g, row.config().type(), x() + 9, rowY + 7, 2, 0xFFE0E0E0);
            int textX = x() + 23, textWidth = Math.max(0, deleteX() - textX - 2);
            String name = QuickMenuLogic.fitText(row.name(), textWidth, font::width);
            boolean confirming = row.name().equals(pending);
            Component summary = confirming
                    ? Component.translatable("gui.excavation3d.favorite.delete_confirm", row.name())
                    : Component.literal(row.summary());
            String detail = QuickMenuLogic.fitText(summary.getString(), textWidth, font::width);
            g.drawString(font, name, textX, rowY + 2, 0xE0E0E0);
            g.drawString(font, detail, textX, rowY + 13, confirming ? 0xFFDD55 : 0x909090);
            g.drawString(font, "×", deleteX() + 4, rowY + 7, confirming ? 0xFFDD55 : 0xFF7070);
            if (i == hovered && mx < trackX()) {
                if (mx >= deleteX()) tooltip = confirming ? summary
                        : Component.translatable("gui.excavation3d.favorite.delete_hint", row.name());
                else if (!name.equals(row.name()) || !detail.equals(summary.getString())) {
                    tooltip = Component.literal(row.name()).append("\n").append(summary);
                }
            }
        }
        g.disableScissor();
        if (layout.maxScroll(rows.size()) > 0) {
            PanelScroll bar = scrollbar();
            g.fill(trackX(), layout.listTop(), trackX() + 4, layout.listBottom(), 0x183F4B59);
            g.fill(trackX(), bar.thumbY(scroll), trackX() + 4, bar.thumbY(scroll) + bar.thumbHeight(), 0x886B7888);
        }
        if (tooltip != null) g.renderTooltip(font, tooltip, mx, my);
    }
}
