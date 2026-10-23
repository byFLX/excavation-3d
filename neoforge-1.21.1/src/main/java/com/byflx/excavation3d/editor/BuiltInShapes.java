package com.byflx.excavation3d.editor;

import com.byflx.excavation3d.shape.ShapeConfig;
import com.byflx.excavation3d.shape.ShapeParams;
import com.byflx.excavation3d.shape.ShapeTemplates;
import com.byflx.excavation3d.shape.ShapeType;
import java.util.List;
import java.util.Optional;

/** Fixed wheel/shortcut order. IDs are separate from user favorite names. */
public final class BuiltInShapes {
    public record Entry(String id, String translationKey, ShapeConfig config) { }
    public static final List<Entry> ALL = List.of(
            entry("tunnel_small", new ShapeParams.Cuboid(1, 2, 1)),
            entry("tunnel_large", new ShapeParams.Cuboid(3, 3, 1)),
            // For a downward hit, local height follows horizontal player facing and depth points down.
            // Thus a level 5 by 5 floor is local 5 x 5 x 1, not a five-block-deep trench (5 x 1 x 5).
            entry("floor", new ShapeParams.Cuboid(5, 5, 1)),
            entry("stairs", ShapeTemplates.defaults(ShapeType.STAIR)));
    private static Entry entry(String id, ShapeParams params) {
        return new Entry("@builtin/" + id, "gui.excavation3d.builtin." + id, ShapeConfig.of(params));
    }
    public static Optional<Entry> find(String id) { return ALL.stream().filter(e -> e.id().equals(id)).findFirst(); }
    private BuiltInShapes() { }
}
