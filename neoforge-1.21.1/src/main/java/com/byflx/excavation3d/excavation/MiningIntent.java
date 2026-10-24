package com.byflx.excavation3d.excavation;

import java.util.function.Predicate;

/** Pure evaluation of a tool's declared mining rules, independent of game registries. */
public final class MiningIntent {
    private MiningIntent() {
    }

    /** A matching speed or positive drops rule declares intent; a denial alone does not. */
    public static <R> boolean matches(Iterable<R> rules, Predicate<R> matchesBlock,
            Predicate<R> hasSpeed, Predicate<R> grantsDrops) {
        for (R rule : rules) {
            if (matchesBlock.test(rule) && (hasSpeed.test(rule) || grantsDrops.test(rule))) {
                return true;
            }
        }
        return false;
    }
}
