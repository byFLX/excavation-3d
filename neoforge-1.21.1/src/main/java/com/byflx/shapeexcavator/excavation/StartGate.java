package com.byflx.shapeexcavator.excavation;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiPredicate;
import net.minecraft.core.BlockPos;

/**
 * Decides when a start block counts as successfully mined (plan §10: a fired break event is no
 * proof of success).
 *
 * <ol>
 *   <li>{@link #attempt}: the player's {@code BreakEvent} for the start block. Any later listener
 *       may still cancel it, so this alone never starts anything.</li>
 *   <li>{@link #confirm}: called from inside the same player's own {@code destroyBlock}, after the
 *       event was not cancelled and every later gate of the break path passed. Another player's
 *       break of the same block never confirms this player's attempt.</li>
 *   <li>{@link #drain}: at the end of the tick only confirmed attempts whose start block is
 *       really gone start; every other attempt is dropped.</li>
 * </ol>
 *
 * @param <P> what the caller needs to start the operation
 */
public final class StartGate<P> {
    private static final class Attempt<P> {
        final BlockPos pos;
        final P payload;
        boolean confirmed;

        Attempt(BlockPos pos, P payload) {
            this.pos = pos;
            this.payload = payload;
        }
    }

    private final Map<UUID, Attempt<P>> attempts = new LinkedHashMap<>();

    /** Records an unconfirmed attempt; replaces any earlier attempt of the same player. */
    public void attempt(UUID player, BlockPos pos, P payload) {
        attempts.put(player, new Attempt<>(pos.immutable(), payload));
    }

    /**
     * Marks the player's attempt at {@code pos} as having passed the whole break path.
     *
     * @return whether a matching attempt existed
     */
    public boolean confirm(UUID player, BlockPos pos) {
        Attempt<P> attempt = attempts.get(player);
        if (attempt == null || !attempt.pos.equals(pos)) {
            return false;
        }
        attempt.confirmed = true;
        return true;
    }

    public boolean has(UUID player) {
        return attempts.containsKey(player);
    }

    public void forget(UUID player) {
        attempts.remove(player);
    }

    public void clear() {
        attempts.clear();
    }

    /**
     * Ends the tick: returns the payloads of confirmed attempts whose start block is gone, in
     * attempt order, and forgets all attempts.
     *
     * @param startBlockGone whether the block at the start position was really removed
     */
    public Map<UUID, P> drain(BiPredicate<BlockPos, P> startBlockGone) {
        Map<UUID, P> started = new LinkedHashMap<>();
        for (Iterator<Map.Entry<UUID, Attempt<P>>> it = attempts.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Attempt<P>> entry = it.next();
            it.remove();
            Attempt<P> attempt = entry.getValue();
            if (attempt.confirmed && startBlockGone.test(attempt.pos, attempt.payload)) {
                started.put(entry.getKey(), attempt.payload);
            }
        }
        return started;
    }
}
