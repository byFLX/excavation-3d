package com.byflx.shapeexcavator.editor;

/** Column and footer geometry in scaled GUI pixels (Minecraft's minimum is 320 x 240). */
public record EditorLayout(int leftWidth, int previewLeft, int previewRight, int contentBottom,
                           int statusY, int summaryY, int actionY) {
    public static EditorLayout fit(int width, int height, int margin, int lineHeight) {
        int leftWidth = Math.clamp((int) (width * 0.36F), 136, 190);
        int actionY = height - 24;
        int summaryY = actionY - 22;
        int statusY = summaryY - 2 * lineHeight - 4;
        return new EditorLayout(leftWidth, margin + leftWidth + 8, width - margin,
                statusY - 3, statusY, summaryY, actionY);
    }
}
