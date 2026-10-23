package com.byflx.shapeexcavator.editor;

import com.byflx.shapeexcavator.shape.ShapeConfig;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.ToIntFunction;

/** Pure timing, radial selection, text fitting and per-player last-shape memory. */
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
        public int pages() { return Math.max(1, (count + PAGE_SIZE - 1) / PAGE_SIZE); }
        public int offset() { return page * PAGE_SIZE; }
        public int segments() { return Math.max(0, Math.min(PAGE_SIZE, count - offset())); }
        public double angle(int segment) { return -Math.PI / 2 + segment * Math.PI * 2 / segments(); }
        public double labelRadius() { return deadZone + (radius - deadZone) * 0.5; }
        public double scale() { return radius / 88.0; }
        public double labelX(int segment) { return centerX + Math.cos(angle(segment)) * labelRadius(); }
        public double labelY(int segment) { return centerY + Math.sin(angle(segment)) * labelRadius(); }
        /** Bounds of the small name below the icon, including a margin at each sector edge. */
        public double labelWidth(int segment) {
            double scale = scale();
            for (int width = 100; width > 0; width--) {
                boolean fits = true;
                for (int side : new int[] {-1, 1}) {
                    for (int y : new int[] {7, 15}) {
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
                double top = labelY(segment) - centerY + 7 * scale;
                double bottom = top + 8 * scale;
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
            return number >= 1 && number <= segments() ? offset() + number - 1 : -1;
        }
        public int highlighted(double mouseX, double mouseY) {
            double dx = mouseX - centerX, dy = mouseY - centerY;
            if (!Double.isFinite(dx) || !Double.isFinite(dy)
                    || Math.hypot(dx, dy) <= deadZone || segments() == 0) return -1;
            // Beyond the ring still selects by angle, like a weapon wheel.
            return offset() + segmentAtAngle(Math.atan2(dy, dx), segments());
        }
    }

    public static Layout layout(int screenWidth, int screenHeight, int count, int requestedPage) {
        count = Math.max(0, count);
        int pages = Math.max(1, (count + PAGE_SIZE - 1) / PAGE_SIZE);
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

    /** Tool and preset revisions differ; only the shape's configuration determines the marker. */
    public static boolean isActive(Entry entry, ShapeConfig activeShape, ShapeConfig entryShape) {
        return entry.kind() == Kind.OFF ? activeShape == null
                : activeShape != null && entryShape != null
                        && activeShape.withRevision(0).equals(entryShape.withRevision(0));
    }

    /** A single line, shortened at code-point boundaries with an ellipsis when needed. */
    public static String fitText(String text, int maxWidth, ToIntFunction<String> width) {
        if (maxWidth <= 0) return "";
        text = text.replace('\n', ' ').replace('\r', ' ');
        if (width.applyAsInt(text) <= maxWidth) return text;
        String ellipsis = "…";
        if (width.applyAsInt(ellipsis) > maxWidth) return "";
        int end = text.length();
        while (end > 0) {
            end = text.offsetByCodePoints(end, -1);
            String candidate = text.substring(0, end).stripTrailing() + ellipsis;
            if (width.applyAsInt(candidate) <= maxWidth) return candidate;
        }
        return ellipsis;
    }

    public static String fitHint(String full, String compact, int maxWidth, ToIntFunction<String> width) {
        return fitText(width.applyAsInt(full) <= maxWidth ? full : compact, maxWidth, width);
    }

    public enum Kind { OFF, LAST, PRESET, EMPTY }
    public record Entry(Kind kind, String name) { }

    public static List<Entry> entries(List<String> presets, boolean hasLast) {
        List<Entry> result = new ArrayList<>();
        result.add(new Entry(Kind.OFF, ""));
        if (hasLast) result.add(new Entry(Kind.LAST, ""));
        PresetShortcuts.sorted(presets).stream()
                .forEach(name -> result.add(new Entry(Kind.PRESET, name)));
        if (presets.isEmpty()) result.add(new Entry(Kind.EMPTY, ""));
        return List.copyOf(result);
    }

    public static final class LastShape<T> {
        private record Removal<T>(UUID player, T shape) { }
        private final Map<UUID, T> shapes = new HashMap<>();
        private final LinkedHashMap<Integer, Removal<T>> pending = new LinkedHashMap<>();
        /** Caller records only a confirmed successful removal; Off on an empty tool keeps the last shape. */
        public void remember(UUID player, T shape) { if (shape != null) shapes.put(player, shape); }
        public Optional<T> find(UUID player) { return Optional.ofNullable(shapes.get(player)); }

        /** Session-bound bookkeeping lives beyond the editor screen that sent Remove. */
        public void expectRemoval(int sessionId, UUID player, T shape) {
            if (shape == null) return;
            pending.put(sessionId, new Removal<>(player, shape));
            if (pending.size() > 64) pending.pollFirstEntry();
        }

        public void onResult(int sessionId, boolean accepted) {
            Removal<T> removal = pending.remove(sessionId);
            if (accepted && removal != null) remember(removal.player(), removal.shape());
        }

        public void clearPending() { pending.clear(); }
    }

    private QuickMenuLogic() { }
}
