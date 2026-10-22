package com.byflx.shapeexcavator.shape;

/**
 * Pure text parser for numeric fields (plan §4). Never throws for user text and never corrects a
 * value silently; surrounding whitespace is trimmed and the length is bounded before parsing.
 */
public final class NumericInput {
    public static final int MAX_LENGTH = 6;

    public enum Problem {
        EMPTY,
        TOO_LONG,
        NEGATIVE,
        DECIMAL,
        NOT_INTEGER,
        BELOW_MIN,
        ABOVE_MAX
    }

    public sealed interface Result permits Valid, Invalid {
        default boolean isValid() {
            return this instanceof Valid;
        }
    }

    public record Valid(int value) implements Result {
    }

    public record Invalid(Problem problem) implements Result {
    }

    private NumericInput() {
    }

    public static Result parse(String text, int min, int max) {
        if (min > max) {
            throw new IllegalArgumentException("min " + min + " > max " + max);
        }
        if (text == null) {
            return new Invalid(Problem.EMPTY);
        }
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return new Invalid(Problem.EMPTY);
        }
        if (trimmed.length() > MAX_LENGTH) {
            return new Invalid(Problem.TOO_LONG);
        }
        if (trimmed.charAt(0) == '-' && isAsciiDigits(trimmed, 1)) {
            return new Invalid(Problem.NEGATIVE);
        }
        if (trimmed.indexOf('.') >= 0 || trimmed.indexOf(',') >= 0) {
            return new Invalid(Problem.DECIMAL);
        }
        if (!isAsciiDigits(trimmed, 0)) {
            return new Invalid(Problem.NOT_INTEGER);
        }
        // At most MAX_LENGTH ASCII digits, so this fits in an int.
        int value = Integer.parseInt(trimmed);
        if (value < min) {
            return new Invalid(Problem.BELOW_MIN);
        }
        if (value > max) {
            return new Invalid(Problem.ABOVE_MAX);
        }
        return new Valid(value);
    }

    private static boolean isAsciiDigits(String text, int from) {
        if (from >= text.length()) {
            return false;
        }
        for (int i = from; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }
}
