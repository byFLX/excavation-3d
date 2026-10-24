package com.byflx.excavation3d.editor;

import com.byflx.excavation3d.shape.ShapeConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.ToIntFunction;

/** Pure timing, radial selection, text fitting and favorite deletion state. */
public final class QuickMenuLogic {
    /** Adjust this constant to change the tap/hold boundary. */
    public static final long HOLD_MS = 250L;
    public static final int PAGE_SIZE = 8;
    public static final long OPEN_MS = 120L;
    public static final long HOVER_MS = 80L;
    public static final double SEGMENT_GAP = 0.018;

    public enum Gesture { NONE, TAP, HOLD }

    public static final class Hold {
        private long start;
        private boolean pressed, opened;

        public Gesture update(boolean down, long now) {
            if (down && !pressed) { start = now; pressed = true; opened = false; }
            if (pressed && !opened && now - start >= HOLD_MS) {
                opened = true;
                if (!down) { pressed = false; }
                return Gesture.HOLD;
            }
            if (!down && pressed) {
                pressed = false;
                return opened ? Gesture.NONE : Gesture.TAP;
            }
            return Gesture.NONE;
        }

        public void cancel() { pressed = false; opened = false; }
    }

    /** Segment zero points up, with the other segments following clockwise. */
    public record Layout(double centerX, double centerY, double radius, double deadZone, int count, int page) {
        public int pages() { return Math.max(1, (count - 1 + PAGE_SIZE - 2) / (PAGE_SIZE - 1)); }
        public int offset() { return page * (PAGE_SIZE - 1); }
        /** Off is pinned to the top on every page. */
        public int index(int segment) { return segment == 0 ? 0 : offset() + segment; }
        public int segments() { return Math.max(0, Math.min(PAGE_SIZE, count - offset())); }
        public double angle(int segment) { return -Math.PI / 2 + segment * Math.PI * 2 / segments(); }
        public double labelRadius() {
            // The area centroid of a half annulus sits inward from the radial midpoint.
            if (segments() == 2) return 4 * (radius * radius * radius - deadZone * deadZone * deadZone)
                    / (3 * Math.PI * (radius * radius - deadZone * deadZone));
            return deadZone + (radius - deadZone) * 0.5;
        }
        public double scale() { return radius / 88.0; }
        public double labelX(int segment) { return centerX + Math.cos(angle(segment)) * labelRadius(); }
        public double labelY(int segment) { return centerY + Math.sin(angle(segment)) * labelRadius(); }
        /** Two halves use a horizontal icon/name row centred on the half's area centroid. */
        public double labelTop() { return segments() == 2 ? -7 : 7; }
        public double labelBottom() { return segments() == 2 ? 7 : 15; }
        public double shortcutX(int segment) {
            return centerX + Math.cos(angle(segment)) * (radius - 5 * scale());
        }
        public double shortcutY(int segment) {
            return centerY + Math.sin(angle(segment)) * (radius - 5 * scale()) - 3 * scale();
        }
        /** Bounds of the small name below the icon, including a margin at each sector edge. */
        public double labelWidth(int segment) {
            double scale = scale();
            for (int width = 100; width > 0; width--) {
                // Reserve the full 10 x 6 shortcut box plus a two-pixel margin on each side.
                if (Math.abs(labelX(segment) - shortcutX(segment)) < (width / 2.0 + 7) * scale
                        && labelY(segment) + labelBottom() * scale > shortcutY(segment) - 2 * scale
                        && labelY(segment) + labelTop() * scale < shortcutY(segment) + 8 * scale) continue;
                boolean fits = true;
                for (int side : new int[] {-1, 1}) {
                    for (double y : new double[] {labelTop(), labelBottom()}) {
                        double dx = labelX(segment) - centerX + side * width * scale / 2;
                        double dy = labelY(segment) - centerY + y * scale;
                        double distance = Math.hypot(dx, dy);
                        double delta = Math.atan2(Math.sin(Math.atan2(dy, dx) - angle(segment)),
                                Math.cos(Math.atan2(dy, dx) - angle(segment)));
                        if (distance > radius - 3 * scale || distance < deadZone + 3 * scale
                                || (segments() > 1 && Math.abs(delta) > Math.PI / segments() - SEGMENT_GAP - 0.035)) {
                            fits = false;
                        }
                    }
                }
                // The nearest point of a horizontal rectangle can lie between its corners.
                double nearX = Math.max(0, Math.abs(labelX(segment) - centerX) - width * scale / 2);
                double top = labelY(segment) - centerY + labelTop() * scale;
                double bottom = labelY(segment) - centerY + labelBottom() * scale;
                double nearY = top <= 0 && bottom >= 0 ? 0 : Math.min(Math.abs(top), Math.abs(bottom));
                if (fits && Math.hypot(nearX, nearY) >= deadZone + 3 * scale) return width * scale;
            }
            return 0;
        }
        /** Instructions live above the local vignette, never in the survival HUD below the wheel. */
        public double instructionsY() { return centerY - 102 * scale() - 14; }
        public boolean showInstructions() { return scale() >= 0.8 && instructionsY() >= 6; }
        /** One-based visible number keys; unused keys on the final page do nothing. */
        public int numberSelection(int number) {
            return number >= 1 && number <= segments() ? index(number - 1) : -1;
        }
        public int highlighted(double mouseX, double mouseY) {
            double dx = mouseX - centerX, dy = mouseY - centerY;
            if (!Double.isFinite(dx) || !Double.isFinite(dy)
                    || Math.hypot(dx, dy) <= deadZone || segments() == 0) return -1;
            // Beyond the ring still selects by angle, like a weapon wheel.
            return index(segmentAtAngle(Math.atan2(dy, dx), segments()));
        }
    }

    public static Layout layout(int screenWidth, int screenHeight, int count, int requestedPage) {
        count = Math.max(0, count);
        int pages = Math.max(1, (count - 1 + PAGE_SIZE - 2) / (PAGE_SIZE - 1));
        // Reserve the local vignette, hover expansion, hint and page counter. Never move the crosshair anchor.
        double scale = Math.max(0.01, Math.min(1,
                Math.min((screenWidth / 2.0 - 4) / 102, (screenHeight / 2.0 - 4) / 116)));
        return new Layout(screenWidth / 2.0, screenHeight / 2.0, 88 * scale,
                28 * scale, count, Math.clamp(requestedPage, 0, pages - 1));
    }

    /** Linear elapsed progress; callers may apply easing after clamping. */
    public static double animationProgress(long now, long start, long duration) {
        if (now <= start) return 0;
        if (duration <= 0) return 1;
        return Math.clamp(((double) now - start) / duration, 0, 1);
    }

    /** Radians in screen coordinates (right = 0, down = pi/2), normalised over any turn. */
    public static int segmentAtAngle(double angle, int count) {
        if (count <= 0 || !Double.isFinite(angle)) return -1;
        double turn = Math.PI * 2;
        double shifted = (angle + Math.PI / 2 + Math.PI / count) % turn;
        if (shifted < 0) shifted += turn;
        return Math.min(count - 1, (int) (shifted / turn * count));
    }

    /** Exactly one marker, at the first matching configuration in wheel order. */
    public static int activeIndex(List<Entry> entries, ShapeConfig shape, boolean enabled,
            java.util.function.Function<String, Optional<ShapeConfig>> favorites) {
        if (shape == null || !enabled) return 0;
        for (int i = 1; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            if (entry.kind() != Kind.PRESET) continue;
            Optional<ShapeConfig> config = favorites.apply(entry.name());
            if (config.map(c -> c.withRevision(0).equals(shape.withRevision(0))).orElse(false)) return i;
        }
        return -1;
    }

    /** A single line, shortened at code-point boundaries with an ellipsis when needed. */
    public static String fitText(String text, int maxWidth, ToIntFunction<String> width) {
        if (maxWidth <= 0) return "";
        text = singleLine(text);
        if (width.applyAsInt(text) <= maxWidth) return text;
        String ellipsis = "…";
        if (width.applyAsInt(ellipsis) > maxWidth) return "";
        int end = text.length();
        for (int remaining = text.codePointCount(0, end); remaining > 0; remaining--) {
            end = text.offsetByCodePoints(end, -1);
            String candidate = text.substring(0, end).stripTrailing() + ellipsis;
            if (width.applyAsInt(candidate) <= maxWidth) return candidate;
        }
        return ellipsis;
    }

    public static String singleLine(String text) {
        return text.replaceAll("\\R", " ");
    }

    /** Two bounded wrap attempts; a broken splitter falls back to a complete single line. */
    public static <T> List<T> wrapName(String text, ToIntFunction<String> width,
            java.util.function.BiFunction<String, Integer, List<T>> split,
            java.util.function.Function<String, T> unwrapped) {
        String name = singleLine(text);
        int fullWidth = Math.max(0, width.applyAsInt(name));
        List<T> lines = split.apply(name, Math.max(40, fullWidth / 2 + fullWidth % 2));
        if (lines.size() > 2) lines = split.apply(name, Math.max(40, fullWidth));
        return lines.size() <= 2 ? lines : List.of(unwrapped.apply(name));
    }

    public static String fitHint(String full, String compact, int maxWidth, ToIntFunction<String> width) {
        return fitText(width.applyAsInt(full) <= maxWidth ? full : compact, maxWidth, width);
    }

    /** Centred stack, uniformly fitted so every row's corners stay inside a padded circle. */
    public record HubLayout(double nameY, double imageY, double detailY, double height, double scale) { }

    public static HubLayout hubLayout(double radius, double nameWidth, double nameHeight,
            double imageSize, double detailWidth, double detailHeight) {
        double gap = 3;
        double height = nameHeight + imageSize + detailHeight
                + (imageSize > 0 ? gap : 0) + (detailHeight > 0 ? gap : 0);
        double nameY = -height / 2;
        double imageY = nameY + nameHeight + (imageSize > 0 ? gap : 0);
        double detailY = imageY + imageSize + (detailHeight > 0 ? gap : 0);
        double extent = Math.hypot(nameWidth / 2, Math.max(Math.abs(nameY), Math.abs(nameY + nameHeight)));
        if (imageSize > 0) extent = Math.max(extent,
                Math.hypot(imageSize / 2, Math.max(Math.abs(imageY), Math.abs(imageY + imageSize))));
        if (detailHeight > 0) extent = Math.max(extent,
                Math.hypot(detailWidth / 2, Math.max(Math.abs(detailY), Math.abs(detailY + detailHeight))));
        return new HubLayout(nameY, imageY, detailY, height, Math.min(1, radius / Math.max(1, extent)));
    }

    public enum Kind { OFF, RESUME, PRESET, HINT }
    public record Entry(Kind kind, String name) { }

    public static List<Entry> entries(List<String> favorites) {
        return entries(favorites, null, false, name -> Optional.empty());
    }

    /** Resume is offered only for a paused shape that cannot be selected through a favorite. */
    public static List<Entry> entries(List<String> favorites, ShapeConfig shape, boolean enabled,
            java.util.function.Function<String, Optional<ShapeConfig>> lookup) {
        List<Entry> result = new ArrayList<>();
        result.add(new Entry(Kind.OFF, ""));
        if (shape != null && !enabled && favorites.stream().noneMatch(name -> lookup.apply(name)
                .map(c -> c.withRevision(0).equals(shape.withRevision(0))).orElse(false))) {
            result.add(new Entry(Kind.RESUME, ""));
        }
        favorites.forEach(name -> result.add(new Entry(Kind.PRESET, name)));
        if (favorites.isEmpty()) result.add(new Entry(Kind.HINT, ""));
        return List.copyOf(result);
    }

    /** Moving to any other segment (including the dead zone) cancels a pending deletion. */
    public static final class FavoriteDelete {
        private String pending;
        public String pending() { return pending; }
        public void hover(Entry entry) {
            if (entry == null || entry.kind() != Kind.PRESET || !entry.name().equals(pending)) pending = null;
        }
        public boolean rightClick(Entry entry) {
            if (entry == null || entry.kind() != Kind.PRESET) { pending = null; return false; }
            if (entry.name().equals(pending)) { pending = null; return true; }
            pending = entry.name();
            return false;
        }
    }

    private QuickMenuLogic() { }
}
