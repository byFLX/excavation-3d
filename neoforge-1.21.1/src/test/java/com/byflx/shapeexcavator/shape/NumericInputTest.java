package com.byflx.shapeexcavator.shape;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.byflx.shapeexcavator.shape.NumericInput.Problem;
import org.junit.jupiter.api.Test;

class NumericInputTest {
    private static NumericInput.Result parse(String text) {
        return NumericInput.parse(text, 1, 32);
    }

    @Test
    void rejectsWithExpectedProblem() {
        assertEquals(new NumericInput.Invalid(Problem.EMPTY), parse(""));
        assertEquals(new NumericInput.Invalid(Problem.EMPTY), parse(null));
        assertEquals(new NumericInput.Invalid(Problem.EMPTY), parse("   "));
        assertEquals(new NumericInput.Invalid(Problem.BELOW_MIN), parse("0"));
        assertEquals(new NumericInput.Invalid(Problem.NEGATIVE), parse("-3"));
        assertEquals(new NumericInput.Invalid(Problem.DECIMAL), parse("1.5"));
        assertEquals(new NumericInput.Invalid(Problem.DECIMAL), parse("1,5"));
        assertEquals(new NumericInput.Invalid(Problem.NOT_INTEGER), parse("abc"));
        assertEquals(new NumericInput.Invalid(Problem.NOT_INTEGER), parse("-"));
        assertEquals(new NumericInput.Invalid(Problem.NOT_INTEGER), parse("+5"));
        assertEquals(new NumericInput.Invalid(Problem.NOT_INTEGER), parse("1 2"));
        assertEquals(new NumericInput.Invalid(Problem.NOT_INTEGER), parse("٣"));
        assertEquals(new NumericInput.Invalid(Problem.TOO_LONG), parse("9999999999999"));
        assertEquals(new NumericInput.Invalid(Problem.TOO_LONG), parse("1234567"));
        assertEquals(new NumericInput.Invalid(Problem.ABOVE_MAX), parse("33"));
        assertEquals(new NumericInput.Invalid(Problem.ABOVE_MAX), parse("999999"));
    }

    @Test
    void acceptsIntegersWithoutCorrection() {
        assertEquals(new NumericInput.Valid(7), parse("007"));
        assertEquals(new NumericInput.Valid(7), parse(" 7 "));
        assertEquals(new NumericInput.Valid(1), parse("1"));
        assertEquals(new NumericInput.Valid(32), parse("32"));
    }

    @Test
    void invalidRangeIsAProgrammingError() {
        assertThrows(IllegalArgumentException.class, () -> NumericInput.parse("1", 5, 4));
    }
}
