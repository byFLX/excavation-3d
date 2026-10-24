package com.byflx.excavation3d.shape;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A validation problem as data, for later translation in the editor and server replies.
 *
 * @param field the parameter or derived quantity concerned, e.g. {@code "width"} or {@code "selection"}
 * @param args  numeric details, typically {@code (value, limit)}
 */
public record ShapeIssue(Code code, String field, List<Long> args) {
    public enum Code {
        /** A raw parameter is below 1. Args: value, minimum. */
        VALUE_TOO_SMALL,
        /** Plus arm thickness exceeds min(width, height). Args: arm, maximum. */
        ARM_TOO_THICK,
        /** A derived extent could not be computed without overflow. */
        SIZE_OVERFLOW,
        /** A resulting extent exceeds the axis limit. Args: extent, limit. */
        AXIS_TOO_LARGE,
        /** The generated selection exceeds the block limit. Args: count, limit. */
        SELECTION_TOO_LARGE,
        /** A custom mask selects nothing. Allowed as a draft, never as an active configuration. */
        EMPTY_MASK,
        /** The custom anchor is not a selected cell. Args: x, y, z. */
        ANCHOR_NOT_SELECTED,
        /** The custom anchor is not in the frontmost occupied layer. Args: anchor z, front z. */
        ANCHOR_NOT_IN_FRONT_LAYER,
        /** Args: found version, supported version. */
        UNSUPPORTED_FORMAT_VERSION
    }

    public ShapeIssue {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(field, "field");
        args = List.copyOf(args);
    }

    public static ShapeIssue of(Code code, String field, long... args) {
        List<Long> list = new ArrayList<>(args.length);
        for (long arg : args) {
            list.add(arg);
        }
        return new ShapeIssue(code, field, list);
    }
}
