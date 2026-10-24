package com.byflx.excavation3d.editor;

/** Bounded popup geometry in scaled GUI pixels. Rows scroll beneath a fixed save/status header. */
public record FavoritesLayout(int x, int y, int width, int height) {
    public static final int HEADER = 40;
    public static final int ROW = 24;

    public static FavoritesLayout fit(int screenWidth, int screenHeight, int top, int rows) {
        int width = Math.min(220, Math.max(1, screenWidth - 12));
        int y = Math.clamp(top, 2, Math.max(2, screenHeight - HEADER - ROW - 6));
        int available = Math.max(1, screenHeight - 30 - y);
        int wanted = HEADER + (rows == 0 ? 48 : Math.min(rows, 7) * ROW) + 4;
        return new FavoritesLayout(Math.max(0, screenWidth - 6 - width), y, width,
                Math.min(available, wanted));
    }

    public int listTop() { return y + HEADER; }
    public int listBottom() { return y + height - 4; }
    public int maxScroll(int rows) { return Math.max(0, rows * ROW - (listBottom() - listTop())); }
    public boolean contains(double mx, double my) {
        return mx >= x && mx < x + width && my >= y && my < y + height;
    }
    public int rowAt(double mx, double my, int scroll, int rows) {
        if (!contains(mx, my) || my < listTop() || my >= listBottom()) return -1;
        int row = (int) (my - listTop() + scroll) / ROW;
        return row < rows ? row : -1;
    }
}
