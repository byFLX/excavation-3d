package com.byflx.shapeexcavator.excavation;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class BudgetSchedulerTest {
    @Test
    void perOperationBudgetCapsEachPlayer() {
        assertArrayEquals(new int[] {32, 5}, BudgetScheduler.allocate(new int[] {100, 5}, 32, 256, 0));
    }

    @Test
    void globalBudgetCapsAllPlayersTogether() {
        int[] demands = new int[10];
        Arrays.fill(demands, 40);
        int[] allocation = BudgetScheduler.allocate(demands, 32, 256, 0);
        assertEquals(256, Arrays.stream(allocation).sum());
        assertTrue(Arrays.stream(allocation).allMatch(a -> a <= 32));
    }

    @Test
    void rotatingStartServesEveryoneOverTime() {
        int[] demands = new int[10];
        Arrays.fill(demands, 1000);
        int[] served = new int[10];
        for (int tick = 0; tick < 10; tick++) {
            int[] allocation = BudgetScheduler.allocate(demands, 32, 256, tick);
            for (int i = 0; i < served.length; i++) {
                served[i] += allocation[i];
            }
        }
        // 8 of 10 operations fit per tick; over 10 ticks each one is served 8 times.
        for (int total : served) {
            assertEquals(8 * 32, total);
        }
    }

    @Test
    void finishedOperationsGetNothingAndBadBudgetsAreRejected() {
        assertArrayEquals(new int[] {0, 3}, BudgetScheduler.allocate(new int[] {0, 3}, 32, 256, 1));
        assertArrayEquals(new int[0], BudgetScheduler.allocate(new int[0], 32, 256, 7));
        assertThrows(IllegalArgumentException.class, () -> BudgetScheduler.allocate(new int[] {1}, 0, 256, 0));
        assertThrows(IllegalArgumentException.class, () -> BudgetScheduler.allocate(new int[] {1}, 32, 0, 0));
    }
}
