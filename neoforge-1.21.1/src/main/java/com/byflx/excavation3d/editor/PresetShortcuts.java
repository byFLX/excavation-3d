package com.byflx.excavation3d.editor;

import com.byflx.excavation3d.shape.ShapeConfig;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/** Shape matching and shortcuts in the supplied wheel order; alphabetical sorting is migration-only. */
public final class PresetShortcuts {
    private PresetShortcuts() { }

    public static List<String> sorted(List<String> names) {
        return names.stream().sorted(String.CASE_INSENSITIVE_ORDER.thenComparing(Comparator.naturalOrder())).toList();
    }

    /** One-based slots; absent slots do nothing. */
    public static Optional<String> nth(List<String> names, int number) {
        List<String> ordered = List.copyOf(names);
        return number > 0 && number <= ordered.size() ? Optional.of(ordered.get(number - 1)) : Optional.empty();
    }

    /** Off or an unsaved shape starts at the first/last preset. Revisions do not affect matching. */
    public static Optional<String> cycle(List<String> names, Function<String, Optional<ShapeConfig>> lookup,
            ShapeConfig active, int direction) {
        return cycle(names, lookup, active, direction, null);
    }

    /** A confirmed name disambiguates presets containing identical shapes; stale/deleted names are ignored. */
    public static Optional<String> cycle(List<String> names, Function<String, Optional<ShapeConfig>> lookup,
            ShapeConfig active, int direction, String confirmedName) {
        List<String> ordered = List.copyOf(names);
        if (ordered.isEmpty() || direction == 0) return Optional.empty();
        int current = -1;
        if (active != null) {
            if (confirmedName != null && ordered.contains(confirmedName)
                    && lookup.apply(confirmedName).map(c -> c.withRevision(0).equals(active.withRevision(0))).orElse(false)) {
                current = ordered.indexOf(confirmedName);
            }
            for (int i = 0; i < ordered.size(); i++) {
                if (current >= 0) break;
                if (lookup.apply(ordered.get(i)).map(c -> c.withRevision(0).equals(active.withRevision(0))).orElse(false)) {
                    current = i;
                    break;
                }
            }
        }
        int next = current < 0 ? (direction > 0 ? 0 : ordered.size() - 1)
                : Math.floorMod(current + Integer.signum(direction), ordered.size());
        return Optional.of(ordered.get(next));
    }

}
