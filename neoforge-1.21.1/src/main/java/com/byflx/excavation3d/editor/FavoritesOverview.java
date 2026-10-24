package com.byflx.excavation3d.editor;

import com.byflx.excavation3d.shape.ShapeConfig;
import com.byflx.excavation3d.shape.ShapeParams;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/** Pure editor favorite rows, selection and bounded two-click deletion confirmation. */
public final class FavoritesOverview {
    public static final long DELETE_CONFIRM_MS = 3000;

    public record Row(String name, ShapeConfig config, String summary, boolean active) { }

    private String name = "";
    private String pendingDelete;
    private long deleteStarted;

    public String name() { return name; }
    public void setName(String name) { this.name = name; }

    /** Selection replaces the draft in one history step and prefills the save row. */
    public void select(Row row, EditorDraft draft, DraftSaveState saveState) {
        draft.loadFavorite(row.config());
        saveState.edited();
        name = row.name();
        cancelDelete();
    }

    /** Names are supplied in repository/wheel order; revisions do not affect the marker. */
    public static List<Row> rows(List<String> names, Function<String, Optional<ShapeConfig>> find,
            ShapeConfig current) {
        List<Row> rows = new ArrayList<>();
        boolean matched = false;
        for (String name : names) {
            Optional<ShapeConfig> found = find.apply(name);
            if (found.isEmpty()) continue;
            ShapeConfig config = found.get();
            boolean active = !matched && current != null
                    && config.withRevision(0).equals(current.withRevision(0));
            matched |= active;
            rows.add(new Row(name, config, summary(config), active));
        }
        return List.copyOf(rows);
    }

    /** Extents without generating masks or imposing this server's current limits. */
    public static String summary(ShapeConfig config) {
        long[] size = switch (config.params()) {
            case ShapeParams.Cuboid p -> new long[] {p.width(), p.height(), p.depth()};
            case ShapeParams.Plus p -> new long[] {p.width(), p.height(), p.depth()};
            case ShapeParams.Stair p -> new long[] {p.width(), (long) p.steps() * p.stepHeight(),
                    (long) p.steps() * p.stepDepth()};
            case ShapeParams.Corridor p -> new long[] {p.width(),
                    p.clearance() + ((long) p.steps() - 1) * p.stepHeight(), (long) p.steps() * p.stepDepth()};
            case ShapeParams.Custom p -> new long[] {p.mask().width(), p.mask().height(), p.mask().depth()};
        };
        String type = switch (config.type()) {
            case CUBOID -> "Box";
            case PLUS -> "Plus";
            case STAIR -> "Stairs";
            case CORRIDOR -> "Corridor";
            case CUSTOM -> "Custom";
        };
        boolean swap = config.transform().rotation() % 2 == 1;
        return type + " " + size[swap ? 1 : 0] + " × " + size[swap ? 0 : 1] + " × " + size[2];
    }

    public String pendingDelete(long now) {
        if (pendingDelete != null && (now < deleteStarted || now - deleteStarted >= DELETE_CONFIRM_MS)) {
            cancelDelete();
        }
        return pendingDelete;
    }

    /** True only on a second click on the same favorite before the three-second deadline. */
    public boolean deleteClick(String name, long now) {
        if (name.equals(pendingDelete(now))) {
            cancelDelete();
            return true;
        }
        pendingDelete = name;
        deleteStarted = now;
        return false;
    }

    public void cancelDelete() { pendingDelete = null; }
}
