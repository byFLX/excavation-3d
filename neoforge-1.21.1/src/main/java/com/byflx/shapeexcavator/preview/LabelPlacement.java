package com.byflx.shapeexcavator.preview;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Places the dimension labels of the editor preview next to their axis ends, but never on top of
 * the shape: a label whose wanted spot overlaps the shape's screen rectangle moves to the nearest
 * free side of it, and labels never overlap each other (a later label moves next to an earlier one
 * in its way). When the shape leaves no free spot (zoomed in, small panel), all labels go into a
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

    /** An axis label with its projected origin; the label must stay beyond its own endpoint. */
    public record AxisLabel(Label label, int originX, int originY) { }

    /** No fallback over geometry: an unplaceable label is hidden, retaining its input index. */
    public static List<Optional<Rect>> axisEnds(List<AxisLabel> labels, Rect shape, Rect panel) {
        List<Optional<Rect>> result = new ArrayList<>();
        List<Rect> taken = new ArrayList<>();
        for (AxisLabel axis : labels) {
            Label label = axis.label();
            double dx = label.anchorX() - axis.originX(), dy = label.anchorY() - axis.originY();
            double length = Math.hypot(dx, dy);
            Rect best = null;
            double distance = Double.POSITIVE_INFINITY;
            if (length >= 1) {
                double ux = dx / length, uy = dy / length;
                double reach = Math.abs(ux) * label.width() / 2.0 + Math.abs(uy) * label.height() / 2.0 + 6;
                int wantedX = (int) Math.round(label.anchorX() + ux * reach - label.width() / 2.0);
                int wantedY = (int) Math.round(label.anchorY() + uy * reach - label.height() / 2.0);
                List<Integer> xs = new ArrayList<>(List.of(wantedX, panel.x0(), panel.x1() - label.width(),
                        shape.x0() - GAP - label.width(), shape.x1() + GAP));
                for (Rect other : taken) {
                    xs.add(other.x0() - GAP - label.width());
                    xs.add(other.x1() + GAP);
                }
                for (int y = panel.y0(); y + label.height() <= panel.y1(); y++) {
                    for (int x : xs) {
                        Rect candidate = at(x, y, label.width(), label.height());
                        // The nearest corner must be beyond the plane through the axis end.
                        double nearX = ux >= 0 ? candidate.x0() : candidate.x1();
                        double nearY = uy >= 0 ? candidate.y0() : candidate.y1();
                        if ((nearX - label.anchorX()) * ux + (nearY - label.anchorY()) * uy < GAP
                                || !free(candidate, shape.inflate(GAP), panel, taken)) continue;
                        double cost = Math.pow(x - wantedX, 2) + Math.pow(y - wantedY, 2);
                        if (cost < distance) { best = candidate; distance = cost; }
                    }
                }
            }
            result.add(Optional.ofNullable(best));
            if (best != null) taken.add(best);
        }
        return List.copyOf(result);
    }

    /** Labels near projected edge midpoints, with leaders drawn by the caller. Never form a strip. */
    public static Layout edges(List<Label> labels, Rect panel) {
        int edgeGap = 6;
        List<Rect> placed = new ArrayList<>();
        for (Label label : labels) {
            Rect wanted = clamp(at(label.anchorX() - label.width() / 2,
                    label.anchorY() - label.height() / 2, label.width(), label.height()), panel);
            Rect best = wanted;
            long distance = Long.MAX_VALUE;
            // Search nearby rows and both ends of existing labels, preserving the edge association.
            for (int y = panel.y0; y + label.height() <= panel.y1; y++) {
                List<Integer> xs = new ArrayList<>(List.of(wanted.x0));
                for (Rect other : placed) {
                    xs.add(other.x0 - label.width() - 2 * edgeGap);
                    xs.add(other.x1 + 2 * edgeGap);
                }
                for (int x : xs) {
                    Rect candidate = at(x, y, label.width(), label.height());
                    if (!candidate.inside(panel) || placed.stream().anyMatch(r -> candidate.intersects(r.inflate(edgeGap)))) continue;
                    long dx = x - wanted.x0, dy = y - wanted.y0;
                    long cost = dx * dx + dy * dy;
                    if (cost < distance) { best = candidate; distance = cost; }
                }
            }
            placed.add(best);
        }
        return new Layout(List.copyOf(placed), Integer.MAX_VALUE);
    }

    /**
     * Places every label beside the shape and clear of the labels placed before it or, if one has no
     * free spot, all of them in the bottom strip.
     */
    public static Layout layout(List<Label> labels, Rect shape, Rect panel) {
        List<Rect> placed = new ArrayList<>(labels.size());
        for (Label label : labels) {
            Optional<Rect> rect = place(label.anchorX(), label.anchorY(), label.width(), label.height(), shape, panel,
                    placed);
            if (rect.isEmpty()) {
                return strip(labels, panel);
            }
            placed.add(rect.get());
        }
        return new Layout(List.copyOf(placed), Integer.MAX_VALUE);
    }

    /** {@link #place(int, int, int, int, Rect, Rect, List)} with no other labels in the way. */
    public static Optional<Rect> place(int anchorX, int anchorY, int width, int height, Rect shape, Rect panel) {
        return place(anchorX, anchorY, width, height, shape, panel, List.of());
    }

    /**
     * @param anchorX the projected axis end the label belongs to
     * @param width   label width
     * @param height  label height
     * @param taken   labels already placed; the result keeps a gap to each of them
     * @return the label rectangle, or empty if no spot inside the panel avoids the shape and the
     *         other labels
     */
    public static Optional<Rect> place(int anchorX, int anchorY, int width, int height, Rect shape, Rect panel,
            List<Rect> taken) {
        Rect blocked = shape.inflate(GAP);
        Rect wanted = clamp(new Rect(anchorX - width / 2, anchorY - height / 2, anchorX - width / 2 + width,
                anchorY - height / 2 + height), panel);
        if (free(wanted, blocked, panel, taken)) {
            return Optional.of(wanted);
        }
        Set<Rect> candidates = new LinkedHashSet<>(List.of(
                wanted,
                clamp(at(blocked.x1, wanted.y0, width, height), panel, false, true),
                clamp(at(blocked.x0 - width, wanted.y0, width, height), panel, false, true),
                clamp(at(wanted.x0, blocked.y0 - height, width, height), panel, true, false),
                clamp(at(wanted.x0, blocked.y1, width, height), panel, true, false)));
        // A spot taken by another label: try right next to that label, once per label in the way.
        List<Rect> frontier = new ArrayList<>(candidates);
        for (int round = 0; round < taken.size() && !frontier.isEmpty(); round++) {
            List<Rect> next = new ArrayList<>();
            for (Rect candidate : frontier) {
                for (Rect other : taken) {
                    if (!candidate.intersects(other.inflate(GAP))) {
                        continue;
                    }
                    next.add(clamp(at(candidate.x0, other.y1 + GAP, width, height), panel, true, false));
                    next.add(clamp(at(candidate.x0, other.y0 - GAP - height, width, height), panel, true, false));
                    next.add(clamp(at(other.x1 + 2 * GAP, candidate.y0, width, height), panel, false, true));
                    next.add(clamp(at(other.x0 - 2 * GAP - width, candidate.y0, width, height), panel, false, true));
                }
            }
            frontier = new ArrayList<>();
            for (Rect rect : next) {
                if (candidates.add(rect)) {
                    frontier.add(rect);
                }
            }
        }
        Rect best = null;
        long bestDistance = Long.MAX_VALUE;
        for (Rect candidate : candidates) {
            if (!free(candidate, blocked, panel, taken)) {
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

    private static boolean free(Rect candidate, Rect blocked, Rect panel, List<Rect> taken) {
        if (candidate.intersects(blocked) || !candidate.inside(panel)) {
            return false;
        }
        for (Rect other : taken) {
            if (candidate.intersects(other.inflate(GAP))) {
                return false;
            }
        }
        return true;
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
