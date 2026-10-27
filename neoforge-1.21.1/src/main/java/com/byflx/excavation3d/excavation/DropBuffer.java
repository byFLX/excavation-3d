package com.byflx.excavation3d.excavation;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/** Pure bounded stack packing and delivery. Stack values are copied, never mutated. */
public final class DropBuffer<T> {
    public interface Stacks<T> {
        int count(T stack);
        int limit(T stack);
        boolean same(T left, T right);
        T withCount(T stack, int count);
    }

    private final Stacks<T> stacks;
    private final int capacity;
    private final List<T> packed = new ArrayList<>();

    public DropBuffer(Stacks<T> stacks, int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("Capacity must be positive");
        this.stacks = stacks;
        this.capacity = capacity;
    }

    /** A full buffer is delivered before accepting another stack; no input is discarded. */
    public void add(T input, Consumer<List<T>> flush) {
        int remaining = stacks.count(input);
        for (int i = 0; i < packed.size() && remaining > 0; i++) {
            T existing = packed.get(i);
            if (!stacks.same(existing, input)) continue;
            int moved = Math.min(remaining, Math.max(0, stacks.limit(existing) - stacks.count(existing)));
            if (moved > 0) packed.set(i, stacks.withCount(existing, stacks.count(existing) + moved));
            remaining -= moved;
        }
        while (remaining > 0) {
            if (packed.size() == capacity) flush.accept(drain());
            int moved = Math.min(remaining, Math.max(1, stacks.limit(input)));
            packed.add(stacks.withCount(input, moved));
            remaining -= moved;
        }
    }

    public List<T> drain() {
        List<T> result = List.copyOf(packed);
        packed.clear();
        return result;
    }

    /** insert returns the remainder; overflow itself is packed before spawning. */
    public void deliver(List<T> batch, UnaryOperator<T> insert, Consumer<T> drop) {
        DropBuffer<T> overflow = new DropBuffer<>(stacks, capacity);
        for (T stack : batch) overflow.add(insert.apply(stack), full -> full.forEach(drop));
        overflow.drain().forEach(drop);
    }
}
