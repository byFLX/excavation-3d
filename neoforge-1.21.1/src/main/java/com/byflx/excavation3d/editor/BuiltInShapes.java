package com.byflx.excavation3d.editor;

import com.byflx.excavation3d.shape.ShapeConfig;
import com.byflx.excavation3d.shape.ShapeParams;
import com.byflx.excavation3d.shape.ShapeTemplates;
import com.byflx.excavation3d.shape.ShapeType;
import java.util.List;

/** One-time favorite seeds, in fixed order, with persistent English names. */
public final class BuiltInShapes {
    public record Entry(String name, ShapeConfig config) { }
    public static final List<Entry> ALL = List.of(
            entry("Tunnel 1×2", new ShapeParams.Cuboid(1, 2, 1)),
            entry("Tunnel 3×3", new ShapeParams.Cuboid(3, 3, 1)),
            // For a downward hit, local height follows horizontal player facing and depth points down.
            // Thus a level 5 by 5 floor is local 5 x 5 x 1, not a five-block-deep trench (5 x 1 x 5).
            entry("Floor 5×5", new ShapeParams.Cuboid(5, 5, 1)),
            entry("Stairs", ShapeTemplates.defaults(ShapeType.STAIR)));
    private static Entry entry(String name, ShapeParams params) {
        return new Entry(name, ShapeConfig.of(params));
    }
    private BuiltInShapes() { }
}
