package com.byflx.shapeexcavator.preview;

import java.util.List;

/**
 * Places a dimension label of the editor preview next to its axis end, but never on top of the
 * shape: if the wanted spot overlaps the shape's screen rectangle, the label moves to the nearest
 * free side of it. The result stays inside the panel whenever the panel has room for it.
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

    private static final int GAP = 2;

    private LabelPlacement() {
    }

    /**
     * @param anchorX the projected axis end the label belongs to
     * @param width   label width
     * @param height  label height
     * @return the label rectangle
     */
    public static Rect place(int anchorX, int anchorY, int width, int height, Rect shape, Rect panel) {
        Rect blocked = shape.inflate(GAP);
        Rect wanted = clamp(new Rect(anchorX - width / 2, anchorY - height / 2, anchorX - width / 2 + width,
                anchorY - height / 2 + height), panel);
        if (!wanted.intersects(blocked) && wanted.inside(panel)) {
            return wanted;
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
        // No free side inside the panel (panel smaller than shape plus label): best effort.
        return best != null ? best : wanted;
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
