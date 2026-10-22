package com.byflx.shapeexcavator.net;

import com.byflx.shapeexcavator.config.ServerConfig;
import com.byflx.shapeexcavator.data.ToolShapeData;
import com.byflx.shapeexcavator.registry.ModComponents;
import com.byflx.shapeexcavator.registry.ModItems;
import com.byflx.shapeexcavator.shape.ShapeConfig;
import com.byflx.shapeexcavator.shape.ShapeGenerator;
import com.byflx.shapeexcavator.shape.ShapeLimits;
import com.byflx.shapeexcavator.text.ShapeTexts;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Server-side editor sessions (plan §9). A session binds the real sender to one tool (by its
 * {@link ToolShapeData#toolId()}) in one hotbar slot. "Apply" is accepted only for that player,
 * slot and tool at the expected revision, and only if the shape is valid under the server limits.
 * All methods run on the server main thread.
 */
public final class EditSessions {
    /** Minimum ticks between two apply requests of one player. */
    static final int MIN_TICKS_BETWEEN_APPLIES = 5;
    /** Begin requests per player and window; reopening the editor a few times stays allowed. */
    static final int MAX_BEGINS_PER_WINDOW = 4;
    static final int BEGIN_WINDOW_TICKS = 40;

    private record Session(int id, int slot, UUID toolId) {
    }

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static final Map<UUID, Integer> LAST_APPLY_TICK = new HashMap<>();
    private static final RequestThrottle BEGINS = new RequestThrottle(MAX_BEGINS_PER_WINDOW, BEGIN_WINDOW_TICKS);

    private EditSessions() {
    }

    static void begin(ServerPlayer player, int slot) {
        // Flooding is dropped before any session, tool data or reply is created.
        if (!BEGINS.tryAcquire(player.getUUID(), player.server.getTickCount())) {
            return;
        }
        ShapeLimits limits = ServerConfig.limits();
        ItemStack stack = toolInSelectedSlot(player, slot);
        if (stack == null) {
            SESSIONS.remove(player.getUUID());
            PacketDistributor.sendToPlayer(player, new EditSessionPayload(0, false, 0L, limits.maxAxis(),
                    limits.maxSelected(), reason("not_holding_tool")));
            return;
        }
        ToolShapeData data = stack.get(ModComponents.TOOL_SHAPE.get());
        if (data == null) {
            data = ToolShapeData.fresh();
            stack.set(ModComponents.TOOL_SHAPE.get(), data);
        }
        int id = ThreadLocalRandom.current().nextInt(1, Integer.MAX_VALUE);
        SESSIONS.put(player.getUUID(), new Session(id, slot, data.toolId()));
        PacketDistributor.sendToPlayer(player, new EditSessionPayload(id, true, data.revision(), limits.maxAxis(),
                limits.maxSelected(), Component.empty()));
    }

    static void apply(ServerPlayer player, ApplyShapePayload request) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || session.id() != request.sessionId()) {
            reject(player, request, "session");
            return;
        }
        int now = player.server.getTickCount();
        Integer last = LAST_APPLY_TICK.get(player.getUUID());
        if (last != null && now - last < MIN_TICKS_BETWEEN_APPLIES) {
            reject(player, request, "rate");
            return;
        }
        LAST_APPLY_TICK.put(player.getUUID(), now);

        ItemStack stack = request.slot() == session.slot() ? toolInSelectedSlot(player, session.slot()) : null;
        ToolShapeData data = stack == null ? null : stack.get(ModComponents.TOOL_SHAPE.get());
        if (data == null || !data.toolId().equals(session.toolId())) {
            reject(player, request, "tool_changed");
            return;
        }
        if (data.revision() != request.expectedRevision()) {
            reject(player, request, "revision");
            return;
        }
        ShapeGenerator.Result result = ShapeGenerator.generate(request.config(), ServerConfig.limits());
        if (result instanceof ShapeGenerator.Invalid invalid) {
            PacketDistributor.sendToPlayer(player, new ApplyResultPayload(request.sessionId(), false, data.revision(),
                    Component.translatable("message.shapeexcavator.reject.invalid",
                            ShapeTexts.issue(invalid.issues().get(0)))));
            return;
        }
        long revision = data.revision() + 1;
        ShapeConfig accepted = request.config().withRevision(revision);
        stack.set(ModComponents.TOOL_SHAPE.get(), data.withConfig(accepted, revision, request.worldPreview()));
        PacketDistributor.sendToPlayer(player, new ApplyResultPayload(request.sessionId(), true, revision,
                Component.translatable("message.shapeexcavator.applied")));
    }

    static void end(UUID player) {
        SESSIONS.remove(player);
        LAST_APPLY_TICK.remove(player);
        BEGINS.forget(player);
    }

    static void clear() {
        SESSIONS.clear();
        LAST_APPLY_TICK.clear();
        BEGINS.clear();
    }

    /** The tool in {@code slot}, if that is the selected hotbar slot and holds the tool; otherwise null. */
    private static ItemStack toolInSelectedSlot(ServerPlayer player, int slot) {
        Inventory inventory = player.getInventory();
        if (!Inventory.isHotbarSlot(slot) || inventory.selected != slot) {
            return null;
        }
        ItemStack stack = inventory.getItem(slot);
        return stack.is(ModItems.SHAPE_EXCAVATOR.get()) ? stack : null;
    }

    private static void reject(ServerPlayer player, ApplyShapePayload request, String reason) {
        PacketDistributor.sendToPlayer(player,
                new ApplyResultPayload(request.sessionId(), false, request.expectedRevision(), reason(reason)));
    }

    private static Component reason(String key) {
        return Component.translatable("message.shapeexcavator.reject." + key);
    }
}
