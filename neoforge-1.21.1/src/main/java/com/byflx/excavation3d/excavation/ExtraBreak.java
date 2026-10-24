package com.byflx.excavation3d.excavation;

/**
 * Outcome of mining one extra block through the player's own {@code destroyBlock} (plan §10: a
 * reported break is no proof of success).
 *
 * <p>{@code destroyBlock} returns {@code false} when a listener cancelled the break event, but it
 * also returns {@code true} when the block refused its removal in {@code onDestroyedByPlayer}. Only
 * the block state afterwards proves that the block is really gone.
 */
public enum ExtraBreak {
    /** The break ran and the block is gone. */
    MINED,
    /** The break was cancelled, e.g. by a protection mod; the block is untouched. */
    PROTECTED,
    /** The break ran, but the block refused its removal and is still there. */
    REFUSED;

    /**
     * @param destroyReturned what {@code destroyBlock} returned
     * @param removed whether the block was really removed afterwards
     */
    public static ExtraBreak of(boolean destroyReturned, boolean removed) {
        if (!destroyReturned) {
            return PROTECTED;
        }
        return removed ? MINED : REFUSED;
    }
}
