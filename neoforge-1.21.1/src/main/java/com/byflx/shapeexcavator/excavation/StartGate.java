package com.byflx.shapeexcavator.excavation;

import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiPredicate;
import net.minecraft.core.BlockPos;

/**
 * Decides when a start block counts as successfully mined (plan §10: a fired break event is no
 * proof of success).
 *
 * <ol>
 *   <li>{@link #attempt}: the player's {@code BreakEvent} for the start block. Any later listener
 *       may still cancel it and the block may still refuse its removal, so this alone never starts
 *       anything.</li>
 *   <li>{@link #confirmRemoved}: the start block's drops are handled with this player as breaker.
 *       The player's own {@code destroyBlock} only does that after the block was really removed, so
 *       a refused removal never confirms, and another player's removal of the same block never
 *       confirms this player's attempt.</li>
 *   <li>{@link #drain}: at the end of the tick only confirmed attempts whose start block is
 *       still gone start; every other attempt is dropped.</li>
 * </ol>
 *
 * The single-block key ({@link #setBypass}) blocks new attempts and drops a pending one, so an
 * update that arrives after the break but before the drain still keeps it a single block.
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
    private final Set<UUID> bypass = new HashSet<>();

    /** The single-block key was pressed or released. Pressing it drops the player's pending attempt. */
    public void setBypass(UUID player, boolean active) {
        if (active) {
            bypass.add(player);
            attempts.remove(player);
        } else {
            bypass.remove(player);
        }
    }

    public boolean bypassActive(UUID player) {
        return bypass.contains(player);
    }

    /**
     * Records an unconfirmed attempt; replaces any earlier attempt of the same player.
     *
     * @return {@code false} if the single-block key is held; nothing is recorded then
     */
    public boolean attempt(UUID player, BlockPos pos, P payload) {
        if (bypass.contains(player)) {
            attempts.remove(player);
            return false;
        }
        attempts.put(player, new Attempt<>(pos.immutable(), payload));
        return true;
    }

    /**
     * Marks the player's attempt at {@code pos} as removed by that player's own break.
     *
     * @return whether a matching attempt existed
     */
    public boolean confirmRemoved(UUID player, BlockPos pos) {
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

    /** Drops the player's attempt; the single-block key state stays. */
    public void forget(UUID player) {
        attempts.remove(player);
    }

    /** Drops everything known about the player (logout). */
    public void removePlayer(UUID player) {
        attempts.remove(player);
        bypass.remove(player);
    }

    public void clear() {
        attempts.clear();
        bypass.clear();
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
