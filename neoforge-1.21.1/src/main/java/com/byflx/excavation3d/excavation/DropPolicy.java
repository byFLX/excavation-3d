package com.byflx.excavation3d.excavation;

public enum DropPolicy {
    PLAYER_CHOICE, INVENTORY, START_PILE, VANILLA;

    public DropMode resolve(DropMode choice) {
        return this == PLAYER_CHOICE ? choice : DropMode.valueOf(name());
    }

    public static DropPolicy fromOrdinal(int value) {
        if (value < 0 || value >= values().length) throw new IllegalArgumentException("Invalid drop policy: " + value);
        return values()[value];
    }
}
