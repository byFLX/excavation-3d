package com.byflx.excavation3d.shape;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Objects;

/**
 * Flat form of a {@link ShapeConfig} for the item data component and network payloads (plan §9).
 * Encoding always succeeds. {@link #decode(long)} never throws: structurally broken data (unknown
 * format version, wrong value count, mask words that do not fit the box) is reported as
 * {@link Decoded.Error}, while semantic problems such as a zero width survive decoding and are
 * reported later by {@link ShapeValidator}.
 *
 * <p>Values per type: cuboid {@code w,h,d}; plus {@code w,h,d,arm}; stair
 * {@code w,steps,stepDepth,stepHeight,ascending}; corridor
 * {@code w,clearance,steps,stepDepth,stepHeight,upward}; custom {@code w,h,d,anchorX,anchorY,anchorZ}
 * plus the mask as {@code ceil(w*h*d/64)} words in {@link ShapeMask#toBitSet()} layout. Booleans are 0 or 1.
 */
public record EncodedShape(int formatVersion, ShapeType type, List<Integer> values, List<Long> maskWords,
        int rotation, boolean mirrored) {
    public static final int MAX_VALUES = 6;
    public static final int MAX_MASK_WORDS = wordsFor(
            ShapeLimits.TECHNICAL_MAX_AXIS * ShapeLimits.TECHNICAL_MAX_AXIS * ShapeLimits.TECHNICAL_MAX_AXIS);

    public sealed interface Decoded permits Ok, Error {
    }

    public record Ok(ShapeConfig config) implements Decoded {
    }

    public record Error(String message) implements Decoded {
    }

    public EncodedShape {
        Objects.requireNonNull(type, "type");
        values = List.copyOf(values);
        maskWords = List.copyOf(maskWords);
    }

    public static EncodedShape encode(ShapeConfig config) {
        List<Integer> values = switch (config.params()) {
            case ShapeParams.Cuboid c -> List.of(c.width(), c.height(), c.depth());
            case ShapeParams.Plus p -> List.of(p.width(), p.height(), p.depth(), p.arm());
            case ShapeParams.Stair s ->
                    List.of(s.width(), s.steps(), s.stepDepth(), s.stepHeight(), s.ascending() ? 1 : 0);
            case ShapeParams.Corridor c -> List.of(c.width(), c.clearance(), c.steps(), c.stepDepth(),
                    c.stepHeight(), c.upward() ? 1 : 0);
            case ShapeParams.Custom c -> {
                ShapeMask m = c.mask();
                yield List.of(m.width(), m.height(), m.depth(), m.anchor().x(), m.anchor().y(), m.anchor().z());
            }
        };
        List<Long> words = config.params() instanceof ShapeParams.Custom custom ? words(custom.mask()) : List.of();
        ShapeTransform t = config.transform();
        return new EncodedShape(config.formatVersion(), config.type(), values, words, t.rotation(), t.mirrored());
    }

    public static int wordsFor(int cells) {
        return (cells + 63) / 64;
    }

    public static int expectedValues(ShapeType type) {
        return switch (type) {
            case CUBOID -> 3;
            case PLUS -> 4;
            case STAIR -> 5;
            case CORRIDOR, CUSTOM -> 6;
        };
    }

    public Decoded decode(long revision) {
        if (formatVersion != ShapeConfig.FORMAT_VERSION) {
            return new Error("unsupported format version " + formatVersion);
        }
        if (rotation < 0 || rotation > 3) {
            return new Error("rotation out of range: " + rotation);
        }
        if (values.size() != expectedValues(type)) {
            return new Error(type + " needs " + expectedValues(type) + " values, got " + values.size());
        }
        if (type != ShapeType.CUSTOM && !maskWords.isEmpty()) {
            return new Error(type + " must not carry mask words");
        }
        ShapeParams params;
        switch (type) {
            case CUBOID -> params = new ShapeParams.Cuboid(v(0), v(1), v(2));
            case PLUS -> params = new ShapeParams.Plus(v(0), v(1), v(2), v(3));
            case STAIR -> {
                if (!isFlag(v(4))) {
                    return new Error("ascending must be 0 or 1: " + v(4));
                }
                params = new ShapeParams.Stair(v(0), v(1), v(2), v(3), v(4) == 1);
            }
            case CORRIDOR -> {
                if (!isFlag(v(5))) {
                    return new Error("upward must be 0 or 1: " + v(5));
                }
                params = new ShapeParams.Corridor(v(0), v(1), v(2), v(3), v(4), v(5) == 1);
            }
            case CUSTOM -> {
                Object mask = decodeMask();
                if (mask instanceof Error error) {
                    return error;
                }
                params = new ShapeParams.Custom((ShapeMask) mask);
            }
            default -> throw new IllegalStateException("unreachable: " + type);
        }
        return new Ok(new ShapeConfig(formatVersion, params, new ShapeTransform(rotation, mirrored), revision));
    }

    /** Returns a {@link ShapeMask} or an {@link Error}; bounds are checked before any allocation. */
    private Object decodeMask() {
        int w = v(0);
        int h = v(1);
        int d = v(2);
        int max = ShapeLimits.TECHNICAL_MAX_AXIS;
        if (w < 1 || w > max || h < 1 || h > max || d < 1 || d > max) {
            return new Error("custom mask size out of range: " + w + "x" + h + "x" + d);
        }
        int size = w * h * d;
        if (maskWords.size() != wordsFor(size)) {
            return new Error("custom mask needs " + wordsFor(size) + " words, got " + maskWords.size());
        }
        long[] raw = new long[maskWords.size()];
        for (int i = 0; i < raw.length; i++) {
            raw[i] = maskWords.get(i);
        }
        try {
            return ShapeMask.of(w, h, d, BitSet.valueOf(raw), new Cell(v(3), v(4), v(5)));
        } catch (IllegalArgumentException e) {
            return new Error("invalid custom mask: " + e.getMessage());
        }
    }

    private int v(int index) {
        return values.get(index);
    }

    private static boolean isFlag(int value) {
        return value == 0 || value == 1;
    }

    private static List<Long> words(ShapeMask mask) {
        long[] raw = mask.toBitSet().toLongArray();
        int count = wordsFor(mask.width() * mask.height() * mask.depth());
        List<Long> words = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            // toLongArray() drops trailing zero words; pad so the length always matches the box.
            words.add(i < raw.length ? raw[i] : 0L);
        }
        return words;
    }
}
