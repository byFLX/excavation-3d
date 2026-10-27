package com.byflx.excavation3d.excavation;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class DropBufferTest {
    private record Stack(String item, String components, int count, int max) { }
    private static final DropBuffer.Stacks<Stack> STACKS = new DropBuffer.Stacks<>() {
        public int count(Stack stack) { return stack.count(); }
        public int limit(Stack stack) { return stack.max(); }
        public boolean same(Stack a, Stack b) { return a.item().equals(b.item()) && a.components().equals(b.components()); }
        public Stack withCount(Stack stack, int count) { return new Stack(stack.item(), stack.components(), count, stack.max()); }
    };
    private static Stack stone(int count) { return new Stack("stone", "", count, 64); }
    private static int total(List<Stack> stacks) { return stacks.stream().mapToInt(Stack::count).sum(); }

    @Test void packsFullStacksAndRemainderWithoutMutatingInput() {
        DropBuffer<Stack> buffer = new DropBuffer<>(STACKS, 256);
        Stack input = stone(70);
        buffer.add(input, batch -> fail("Unexpected flush"));
        buffer.add(stone(60), batch -> fail("Unexpected flush"));
        assertEquals(List.of(stone(64), stone(64), stone(2)), buffer.drain());
        assertEquals(70, input.count());
        assertTrue(buffer.drain().isEmpty());
    }

    @Test void preservesComponentsAndDifferentItems() {
        DropBuffer<Stack> buffer = new DropBuffer<>(STACKS, 256);
        List<Stack> input = List.of(stone(3), new Stack("stone", "named", 2, 64), new Stack("dirt", "", 4, 64));
        input.forEach(stack -> buffer.add(stack, batch -> fail("Unexpected flush")));
        assertEquals(input, buffer.drain());
    }

    @Test void respectsSixteenAndOneItemStackLimits() {
        DropBuffer<Stack> buffer = new DropBuffer<>(STACKS, 256);
        buffer.add(new Stack("pearl", "", 35, 16), batch -> fail("Unexpected flush"));
        buffer.add(new Stack("tool", "", 2, 1), batch -> fail("Unexpected flush"));
        assertEquals(List.of(16, 16, 3, 1, 1), buffer.drain().stream().map(Stack::count).toList());
    }

    @Test void capacityFlushNeverLosesLargeLootAndNeverExceedsCap() {
        DropBuffer<Stack> buffer = new DropBuffer<>(STACKS, 2);
        List<Stack> delivered = new ArrayList<>();
        buffer.add(stone(1000), batch -> {
            assertEquals(2, batch.size());
            delivered.addAll(batch);
        });
        delivered.addAll(buffer.drain());
        assertEquals(1000, total(delivered));
        assertTrue(delivered.stream().allMatch(stack -> stack.count() <= stack.max()));
    }

    @Test void inventoryPartiallyAcceptsAndOverflowIsMerged() {
        DropBuffer<Stack> buffer = new DropBuffer<>(STACKS, 256);
        int[] space = {10};
        List<Stack> dropped = new ArrayList<>();
        buffer.deliver(List.of(stone(5), stone(64), stone(8)), stack -> {
            int inserted = Math.min(space[0], stack.count());
            space[0] -= inserted;
            return STACKS.withCount(stack, stack.count() - inserted);
        }, dropped::add);
        assertEquals(List.of(stone(64), stone(3)), dropped);
        assertEquals(77, 10 + total(dropped));
    }

    @Test void fullInventoryDropsEverythingAndEmptyInventoryDropsNothing() {
        DropBuffer<Stack> buffer = new DropBuffer<>(STACKS, 2);
        List<Stack> dropped = new ArrayList<>();
        buffer.deliver(List.of(stone(64), stone(9)), stack -> stack, dropped::add);
        assertEquals(73, total(dropped));
        buffer.deliver(List.of(stone(64)), stack -> stone(0), stack -> fail("Inventory accepted everything"));
    }

    @Test void interruptedFlushDeliversExactlyTheCollectedPrefixOnce() {
        DropBuffer<Stack> buffer = new DropBuffer<>(STACKS, 2);
        for (int i = 0; i < 5; i++) buffer.add(stone(1), batch -> fail("Unexpected flush"));
        List<Stack> dropped = new ArrayList<>();
        buffer.deliver(buffer.drain(), stack -> stack, dropped::add);
        buffer.deliver(buffer.drain(), stack -> stack, dropped::add);
        assertEquals(List.of(stone(5)), dropped);
    }

    @Test void ignoresEmptyStacksAndRejectsZeroCapacity() {
        assertThrows(IllegalArgumentException.class, () -> new DropBuffer<>(STACKS, 0));
        DropBuffer<Stack> buffer = new DropBuffer<>(STACKS, 1);
        buffer.add(stone(0), batch -> fail("Unexpected flush"));
        assertTrue(buffer.drain().isEmpty());
    }

    @Test void modeOrdinalsAreValidatedAndPolicyOverridesChoice() {
        for (DropMode mode : DropMode.values()) {
            assertEquals(mode, DropMode.fromOrdinal(mode.ordinal()));
            assertEquals(mode, DropPolicy.PLAYER_CHOICE.resolve(mode));
            assertEquals(DropMode.VANILLA, DropPolicy.VANILLA.resolve(mode));
            assertEquals(DropMode.START_PILE, DropPolicy.START_PILE.resolve(mode));
            assertEquals(DropMode.INVENTORY, DropPolicy.INVENTORY.resolve(mode));
        }
        for (int value = 3; value < 256; value++) {
            int invalid = value;
            assertThrows(IllegalArgumentException.class, () -> DropMode.fromOrdinal(invalid));
        }
        assertThrows(IllegalArgumentException.class, () -> DropMode.fromOrdinal(-1));
        assertThrows(IllegalArgumentException.class, () -> DropPolicy.fromOrdinal(4));
        assertThrows(IllegalArgumentException.class, () -> DropPolicy.fromOrdinal(-1));
        assertEquals(DropMode.INVENTORY, DropMode.VANILLA.next());
    }
}
