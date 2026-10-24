package com.byflx.excavation3d.excavation;

/**
 * Splits one server tick's work budget between running excavations (plan §10). Every checked
 * position counts, including air and rejected ones. Each operation gets at most
 * {@code perOperation}; the sum never exceeds {@code global}. The starting index rotates every
 * tick so that no player is permanently served last when the global budget runs out.
 */
public final class BudgetScheduler {
    private BudgetScheduler() {
    }

    /**
     * @param demands      remaining positions per operation
     * @param perOperation budget per operation and tick, at least 1
     * @param global       budget for all operations in this tick, at least 1
     * @param start        index served first; any int, taken modulo the number of operations
     * @return positions each operation may check this tick
     */
    public static int[] allocate(int[] demands, int perOperation, int global, int start) {
        if (perOperation < 1 || global < 1) {
            throw new IllegalArgumentException("budgets must be at least 1: " + perOperation + ", " + global);
        }
        int n = demands.length;
        int[] allocation = new int[n];
        int remaining = global;
        for (int i = 0; i < n && remaining > 0; i++) {
            int index = Math.floorMod(start + i, n);
            int share = Math.min(Math.max(demands[index], 0), Math.min(perOperation, remaining));
            allocation[index] = share;
            remaining -= share;
        }
        return allocation;
    }
}
