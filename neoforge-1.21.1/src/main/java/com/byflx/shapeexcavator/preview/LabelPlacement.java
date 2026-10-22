package com.byflx.shapeexcavator.preview;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Places the dimension labels of the editor preview next to their axis ends, but never on top of
 * the shape: a label whose wanted spot overlaps the shape's screen rectangle moves to the nearest
 * free side of it. When the shape leaves no free side (zoomed in, small panel), all labels go into a
 * strip at the bottom of the panel and the shape is clipped above that strip, so the labels still
 * never cover it.
 */
public final class LabelPlacement {
    /** Screen rectangle, x1/y1 exclusive. */
    public record Rect(int x0, int y0, int x1, int y1) {
        public boolean intersects(Rect other) {
            return x0 < other.x1 && other.x0 < x1 && y0 < other.y1 && other.y0 < y1;
        }

        public boolean inside(Rect outer) {
            return x0 >= outer.x0 && y0 >= outer.y0 && x1 <= outer.x1 && y1 <= outer.y1;
        }

        Rect inflate(int amount) {
            return new Rect(x0 - amount, y0 - amount, x1 + amount, y1 + amount);
        }
    }

    /** A label to place: its projected axis end and its size. */
    public record Label(int anchorX, int anchorY, int width, int height) {
    }

    /**
     * @param labels    the label rectangles, in input order
     * @param shapeMaxY the shape must only be drawn above this y (exclusive); {@link Integer#MAX_VALUE}
     *                  when every label found a free spot beside the shape
     */
    public record Layout(List<Rect> labels, int shapeMaxY) {
        public boolean clipsShape() {
            return shapeMaxY != Integer.MAX_VALUE;
        }
    }

    private static final int GAP = 2;

    private LabelPlacement() {
    }

    /** Places every label beside the shape or, if one has no free spot, all of them in the bottom strip. */
    public static Layout layout(List<Label> labels, Rect shape, Rect panel) {
        List<Rect> placed = new ArrayList<>(labels.size());
        for (Label label : labels) {
            Optional<Rect> rect = place(label.anchorX(), label.anchorY(), label.width(), label.height(), shape, panel);
            if (rect.isEmpty()) {
                return strip(labels, panel);
            }
            placed.add(rect.get());
        }
        return new Layout(List.copyOf(placed), Integer.MAX_VALUE);
    }

    /**
     * @param anchorX the projected axis end the label belongs to
     * @param width   label width
     * @param height  label height
     * @return the label rectangle, or empty if no spot inside the panel avoids the shape
     */
    public static Optional<Rect> place(int anchorX, int anchorY, int width, int height, Rect shape, Rect panel) {
        Rect blocked = shape.inflate(GAP);
        Rect wanted = clamp(new Rect(anchorX - width / 2, anchorY - height / 2, anchorX - width / 2 + width,
                anchorY - height / 2 + height), panel);
        if (!wanted.intersects(blocked) && wanted.inside(panel)) {
            return Optional.of(wanted);
        }
        List<Rect> candidates = List.of(
                clamp(at(blocked.x1, wanted.y0, width, height), panel, false, true),
                clamp(at(blocked.x0 - width, wanted.y0, width, height), panel, false, true),
                clamp(at(wanted.x0, blocked.y0 - height, width, height), panel, true, false),
                clamp(at(wanted.x0, blocked.y1, width, height), panel, true, false));
        Rect best = null;
        long bestDistance = Long.MAX_VALUE;
        for (Rect candidate : candidates) {
            if (candidate.intersects(blocked) || !candidate.inside(panel)) {
                continue;
            }
            long dx = candidate.x0 - wanted.x0;
            long dy = candidate.y0 - wanted.y0;
            long distance = dx * dx + dy * dy;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        return Optional.ofNullable(best);
    }

    /** All labels in rows at the bottom of the panel, left to right; the shape is clipped above them. */
    private static Layout strip(List<Label> labels, Rect panel) {
        int rowHeight = 0;
        for (Label label : labels) {
            rowHeight = Math.max(rowHeight, label.height());
        }
        int[] rows = new int[labels.size()];
        int[] xs = new int[labels.size()];
        int row = 0;
        int x = panel.x0;
        for (int i = 0; i < labels.size(); i++) {
            int width = labels.get(i).width();
            if (x > panel.x0 && x + width > panel.x1) {
                row++;
                x = panel.x0;
            }
            rows[i] = row;
            xs[i] = x;
            x += width + 2 * GAP;
        }
        int rowCount = row + 1;
        int top = Math.max(panel.y0, panel.y1 - rowCount * rowHeight - (rowCount - 1) * GAP);
        List<Rect> placed = new ArrayList<>(labels.size());
        for (int i = 0; i < labels.size(); i++) {
            Label label = labels.get(i);
            int y = top + rows[i] * (rowHeight + GAP);
            placed.add(new Rect(xs[i], y, xs[i] + label.width(), y + label.height()));
        }
        return new Layout(List.copyOf(placed), top - GAP);
    }

    private static Rect at(int x, int y, int width, int height) {
        return new Rect(x, y, x + width, y + height);
    }

    private static Rect clamp(Rect r, Rect panel) {
        return clamp(r, panel, true, true);
    }

    /** Moves {@code r} into the panel along the allowed axes, keeping its size. */
    private static Rect clamp(Rect r, Rect panel, boolean horizontal, boolean vertical) {
        int w = r.x1 - r.x0;
        int h = r.y1 - r.y0;
        int x = r.x0;
        int y = r.y0;
        if (horizontal) {
            x = Math.max(panel.x0, Math.min(x, panel.x1 - w));
        }
        if (vertical) {
            y = Math.max(panel.y0, Math.min(y, panel.y1 - h));
        }
        return new Rect(x, y, x + w, y + h);
    }
}
