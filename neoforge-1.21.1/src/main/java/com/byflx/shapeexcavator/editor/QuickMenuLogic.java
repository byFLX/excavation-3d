package com.byflx.shapeexcavator.editor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Pure timing, compact-list selection and per-player last-shape memory. */
public final class QuickMenuLogic {
    /** Adjust this constant to change the tap/hold boundary. */
    public static final long HOLD_MS = 250L;
    public static final int CENTER_RADIUS = 18;
    public static final int ROW_HEIGHT = 20;
    public static final int MAX_ROWS = 9;

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

    /** A compact scrollable list to the right of the crosshair; the centre always cancels. */
    public record Layout(int centerX, int centerY, int width, int count, int offset, int rows) {
        public int x() { return centerX + CENTER_RADIUS + 6; }
        public int y() { return centerY - rows * ROW_HEIGHT / 2; }
        public int highlighted(double mouseX, double mouseY) {
            if (Math.hypot(mouseX - centerX, mouseY - centerY) <= CENTER_RADIUS
                    || mouseX < x() || mouseX >= x() + width || mouseY < y()) return -1;
            int row = (int)((mouseY - y()) / ROW_HEIGHT);
            int index = offset + row;
            return row < rows && index < count ? index : -1;
        }
    }

    public static Layout layout(int screenWidth, int screenHeight, int count, int requestedOffset) {
        // The list is centred vertically on the crosshair. Reserve room on both ends for
        // the title and footer (page count, scroll hint, two instruction lines).
        int rows = Math.min(count, Math.min(MAX_ROWS, Math.max(1, (screenHeight - 120) / ROW_HEIGHT)));
        int offset = Math.max(0, Math.min(requestedOffset, count - rows));
        return new Layout(screenWidth / 2, screenHeight / 2,
                Math.max(20, Math.min(190, screenWidth / 2 - CENTER_RADIUS - 12)), count, offset, rows);
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
