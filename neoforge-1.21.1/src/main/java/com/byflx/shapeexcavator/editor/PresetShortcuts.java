package com.byflx.shapeexcavator.editor;

import com.byflx.shapeexcavator.shape.ShapeConfig;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/** Shared alphabetical order and shape-value matching for wheel and keyboard selections. */
public final class PresetShortcuts {
    private PresetShortcuts() { }

    public static List<String> sorted(List<String> names) {
        return names.stream().sorted(String.CASE_INSENSITIVE_ORDER.thenComparing(Comparator.naturalOrder())).toList();
    }

    /** One-based slots; absent slots do nothing. */
    public static Optional<String> nth(List<String> names, int number) {
        List<String> sorted = sorted(names);
        return number > 0 && number <= sorted.size() ? Optional.of(sorted.get(number - 1)) : Optional.empty();
    }

    /** Off or an unsaved shape starts at the first/last preset. Revisions do not affect matching. */
    public static Optional<String> cycle(List<String> names, Function<String, Optional<ShapeConfig>> lookup,
            ShapeConfig active, int direction) {
        return cycle(names, lookup, active, direction, null);
    }

    /** A confirmed name disambiguates presets containing identical shapes; stale/deleted names are ignored. */
    public static Optional<String> cycle(List<String> names, Function<String, Optional<ShapeConfig>> lookup,
            ShapeConfig active, int direction, String confirmedName) {
        List<String> sorted = sorted(names);
        if (sorted.isEmpty() || direction == 0) return Optional.empty();
        int current = -1;
        if (active != null) {
            if (confirmedName != null && sorted.contains(confirmedName)
                    && lookup.apply(confirmedName).map(c -> c.withRevision(0).equals(active.withRevision(0))).orElse(false)) {
                current = sorted.indexOf(confirmedName);
            }
            for (int i = 0; i < sorted.size(); i++) {
                if (current >= 0) break;
                if (lookup.apply(sorted.get(i)).map(c -> c.withRevision(0).equals(active.withRevision(0))).orElse(false)) {
                    current = i;
                    break;
                }
            }
        }
        int next = current < 0 ? (direction > 0 ? 0 : sorted.size() - 1)
                : Math.floorMod(current + Integer.signum(direction), sorted.size());
        return Optional.of(sorted.get(next));
    }

    public enum Toggle { OFF, RESTORE, NONE }

    public static Toggle toggle(boolean hasShape, boolean hasLast) {
        return hasShape ? Toggle.OFF : hasLast ? Toggle.RESTORE : Toggle.NONE;
    }
}
