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
    public record Layout(int centerX, int centerY, int radius, int deadZone, int count, int page) {
        public int pages() { return Math.max(1, (count + PAGE_SIZE - 1) / PAGE_SIZE); }
        public int offset() { return page * PAGE_SIZE; }
        public int segments() { return Math.min(PAGE_SIZE, count - offset()); }
        public double angle(int segment) { return -Math.PI / 2 + segment * Math.PI * 2 / segments(); }
        public double labelRadius() { return deadZone + (radius - deadZone) * 0.5; }
        public int labelX(int segment) { return centerX + (int) Math.round(Math.cos(angle(segment)) * labelRadius()); }
        public int labelY(int segment) { return centerY + (int) Math.round(Math.sin(angle(segment)) * labelRadius()); }
        public int labelWidth(int segment) {
            double neighbourSpace = 2 * labelRadius() * Math.sin(Math.PI / Math.max(2, segments())) - 10;
            int x = Math.abs(labelX(segment) - centerX), y = labelY(segment) - centerY;
            int farY = Math.max(Math.abs(y + 4), Math.abs(y + 13));
            double circleEdge = Math.sqrt(Math.max(0, (radius - 2) * (radius - 2) - farY * farY));
            int nearY = y + 4 <= 0 && y + 13 >= 0 ? 0 : Math.min(Math.abs(y + 4), Math.abs(y + 13));
            double hubSpace = 100;
            if (nearY < deadZone + 2) {
                double hubEdge = Math.sqrt((deadZone + 2) * (deadZone + 2) - nearY * nearY);
                hubSpace = 2 * (x - hubEdge - 1);
            }
            // Labels at the sides have less room than those at the top/bottom of the annulus.
            return (int) Math.max(0, Math.min(hubSpace, Math.min(100,
                    Math.min(neighbourSpace, 2 * (circleEdge - x - 1)))));
        }
        public int footerY() { return centerY + radius + 5; }
        public int highlighted(double mouseX, double mouseY) {
            double dx = mouseX - centerX, dy = mouseY - centerY;
            if (Math.hypot(dx, dy) <= deadZone || segments() == 0) return -1;
            // Beyond the ring still selects by angle, like a weapon wheel.
            return offset() + segmentAtAngle(Math.atan2(dy, dx), segments());
        }
    }

    public static Layout layout(int screenWidth, int screenHeight, int count, int requestedPage) {
        count = Math.max(0, count);
        int pages = Math.max(1, (count + PAGE_SIZE - 1) / PAGE_SIZE);
        // Minecraft caps the effective GUI scale at a minimum logical size of 320 x 240.
        // Keep room above for shape details and below for three single-line hints.
        int radius = Math.max(1, Math.min(124, Math.min(screenWidth / 2 - 8, screenHeight / 2 - 44)));
        return new Layout(screenWidth / 2, screenHeight / 2, radius,
                Math.max(1, (int) (radius * 0.43)), count, Math.clamp(requestedPage, 0, pages - 1));
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

    public enum Kind { LAST, PRESET, OFF }
    public record Entry(Kind kind, String name) { }

    public static List<Entry> entries(List<String> presets, boolean hasLast) {
        List<Entry> result = new ArrayList<>();
        if (hasLast) result.add(new Entry(Kind.LAST, ""));
        for (String name : presets) result.add(new Entry(Kind.PRESET, name));
        result.add(new Entry(Kind.OFF, ""));
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
