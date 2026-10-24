package com.byflx.excavation3d.preview;

/**
 * Client-side estimate for one position of the world preview (plan §8). Only {@link #START} and
 * {@link #PRESENT} are expected to be mined; the server still decides, protection included.
 */
public enum CellCategory {
    START,
    PRESENT,
    AIR,
    EXCLUDED,
    UNLOADED;

    public boolean mined() {
        return this == START || this == PRESENT;
    }
}
