package com.byflx.excavation3d.excavation;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.level.BlockDropsEvent;

/** One excavation's drops, including its confirmed start. No loot is generated here. */
final class DropDelivery {
    // At most 256 legal stacks retained, independent of excavation length or loot multipliers.
    private static final int MAX_STACKS = 256;
    // Vanilla Mending multiplies repaired durability by orb XP using int arithmetic. With the
    // vanilla repair ratio (2), 2 * 32767^2 fits an int; giant modded rewards must be split.
    private static final int MAX_ORB_XP = 32767;
    private static final DropBuffer.Stacks<ItemStack> STACKS = new DropBuffer.Stacks<>() {
        public int count(ItemStack stack) { return stack.getCount(); }
        public int limit(ItemStack stack) { return stack.getMaxStackSize(); }
        public boolean same(ItemStack a, ItemStack b) { return ItemStack.isSameItemSameComponents(a, b); }
        public ItemStack withCount(ItemStack stack, int count) { return stack.copyWithCount(count); }
    };
    private final DropBuffer<ItemStack> items = new DropBuffer<>(STACKS, MAX_STACKS);
    private final ServerPlayer player;
    private final ServerLevel startLevel;
    private final BlockPos start;
    private final DropMode mode;
    private Vec3 lastPosition;
    private long experience;

    DropDelivery(ServerPlayer player, ServerLevel level, BlockPos start) {
        this.player = player;
        this.startLevel = level;
        this.start = start;
        this.mode = DropPreferences.effective(player.getUUID());
        this.lastPosition = player.position();
    }

    void rememberPosition() {
        if (player.serverLevel() == startLevel) lastPosition = player.position();
    }

    void capture(BlockDropsEvent event) {
        if (mode == DropMode.VANILLA || event.isCanceled()) return;
        for (ItemEntity entity : event.getDrops()) items.add(entity.getItem(), batch -> deliver(batch, false));
        event.getDrops().clear();
        if (startLevel.getGameRules().getBoolean(GameRules.RULE_DOBLOCKDROPS) && !startLevel.restoringBlockSnapshots)
            experience += event.getDroppedExperience();
        event.setDroppedExperience(0);
        // Leave the event uncancelled: spawnAfterBreak must retain its normal non-XP effects.
        if (experience >= MAX_ORB_XP) flush(false);
    }

    /** Unsafe player lifecycle transitions always deliver in the original dimension. */
    void flush(boolean atStart) {
        if (mode == DropMode.VANILLA) return;
        rememberPosition();
        deliver(items.drain(), atStart);
        if (experience > 0) {
            Vec3 position = destination(atStart);
            ensureLoaded(position);
            while (experience > 0) {
                int value = (int) Math.min(experience, MAX_ORB_XP);
                ExperienceOrb orb = new ExperienceOrb(startLevel, position.x, position.y, position.z, value);
                orb.setDeltaMovement(Vec3.ZERO);
                startLevel.addFreshEntity(orb);
                experience -= value;
            }
        }
    }

    private boolean inventory(boolean atStart) {
        return !atStart && mode == DropMode.INVENTORY && player.isAlive()
                && !player.isRemoved() && !player.hasDisconnected() && player.serverLevel() == startLevel;
    }

    private Vec3 destination(boolean atStart) {
        if (inventory(atStart)) return player.position();
        return startLevel.isLoaded(start) ? Vec3.atCenterOf(start) : lastPosition;
    }

    private void deliver(List<ItemStack> batch, boolean atStart) {
        if (batch.isEmpty()) return;
        Vec3 position = destination(atStart);
        items.deliver(batch, stack -> {
            if (inventory(atStart)) {
                insert(stack);
                player.getInventory().setChanged();
                player.inventoryMenu.broadcastChanges();
            }
            return stack;
        }, stack -> {
            ensureLoaded(position);
            ItemEntity entity = new ItemEntity(startLevel, position.x, position.y, position.z, stack, 0, 0, 0);
            entity.setDefaultPickUpDelay();
            startLevel.addFreshEntity(entity);
        });
    }

    private void insert(ItemStack stack) {
        var inventory = player.getInventory();
        while (!stack.isEmpty()) {
            int slot = inventory.getSlotWithRemainingSpace(stack);
            if (slot < 0) slot = inventory.getFreeSlot();
            if (slot < 0) return;
            ItemStack existing = inventory.getItem(slot);
            int space = Math.min(inventory.getMaxStackSize(), stack.getMaxStackSize()) - existing.getCount();
            if (space <= 0) return;
            int moved = Math.min(space, stack.getCount());
            if (existing.isEmpty()) inventory.setItem(slot, stack.copyWithCount(moved));
            else existing.grow(moved);
            inventory.getItem(slot).setPopTime(5);
            stack.shrink(moved);
        }
        // Inventory.add would discard overflow if the player switched to creative mid-operation.
        // Explicit insertion leaves every remainder available for delivery at the player's feet.
    }

    private void ensureLoaded(Vec3 position) {
        // Normally already loaded. Last-position fallback may need one chunk during logout/stop;
        // load it before spawning so the entities are tracked and saved, rather than lost.
        BlockPos pos = BlockPos.containing(position);
        if (!startLevel.isLoaded(pos)) startLevel.getChunk(pos);
    }
}
