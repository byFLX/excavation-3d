package com.byflx.excavation3d.excavation;

/** Wire order is stable within protocol 6. */
public enum DropMode {
    INVENTORY, START_PILE, VANILLA;

    public DropMode next() { return values()[(ordinal() + 1) % values().length]; }

    public static DropMode fromOrdinal(int value) {
        if (value < 0 || value >= values().length) throw new IllegalArgumentException("Invalid drop mode: " + value);
        return values()[value];
    }
}
