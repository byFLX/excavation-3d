package com.byflx.excavation3d.shape;

import java.util.Objects;

/** Versioned, immutable shape configuration (plan §9). Validity is decided by {@link ShapeGenerator}. */
public record ShapeConfig(int formatVersion, ShapeParams params, ShapeTransform transform, long revision) {
    public static final int FORMAT_VERSION = 1;

    public ShapeConfig {
        Objects.requireNonNull(params, "params");
        Objects.requireNonNull(transform, "transform");
    }

    public static ShapeConfig of(ShapeParams params) {
        return new ShapeConfig(FORMAT_VERSION, params, ShapeTransform.IDENTITY, 0L);
    }

    public static ShapeConfig of(ShapeParams params, ShapeTransform transform) {
        return new ShapeConfig(FORMAT_VERSION, params, transform, 0L);
    }

    public ShapeType type() {
        return params.type();
    }

    public ShapeConfig withParams(ShapeParams newParams) {
        return new ShapeConfig(formatVersion, newParams, transform, revision);
    }

    public ShapeConfig withTransform(ShapeTransform newTransform) {
        return new ShapeConfig(formatVersion, params, newTransform, revision);
    }

    public ShapeConfig withRevision(long newRevision) {
        return new ShapeConfig(formatVersion, params, transform, newRevision);
    }
}
