package com.byflx.excavation3d.preview;

import java.util.Objects;

/** Pure target hysteresis. Time is measured in ticks, never in the number of render frames. */
public final class StableTarget<T> {
    public static final int HOLD_TICKS = 3;
    private T current, pending;
    private boolean waiting;
    private long pendingSince;

    public T current() { return current; }

    /** Nearby switches and misses must persist; deliberate moves to a distant target are immediate. */
    public T select(T candidate, long tick, boolean nearby) {
        if (current == null || !nearby || Objects.equals(current, candidate)) {
            current = candidate;
            waiting = false;
            pending = null;
        } else {
            if (!waiting || !Objects.equals(pending, candidate)) {
                pending = candidate;
                pendingSince = tick;
                waiting = true;
            }
            if (tick - pendingSince >= HOLD_TICKS) {
                current = candidate;
                pending = null;
                waiting = false;
            }
        }
        return current;
    }

    public void reset() { current = null; pending = null; waiting = false; }
}
