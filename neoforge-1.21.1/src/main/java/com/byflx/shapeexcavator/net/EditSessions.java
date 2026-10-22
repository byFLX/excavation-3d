package com.byflx.shapeexcavator.net;

import com.byflx.shapeexcavator.config.ServerConfig;
import com.byflx.shapeexcavator.data.ToolShapeData;
import com.byflx.shapeexcavator.registry.ModComponents;
import com.byflx.shapeexcavator.registry.ModTags;
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
 * Server-side editor sessions (plan §2a, §9). A session binds the real sender to one hotbar slot,
 * the stack instance in it and the {@link ToolShapeData#toolId()} of its shape (null while the tool
 * has none). "Apply" and "Remove shape" are accepted only if {@link SessionBinding} accepts that
 * binding at the expected revision and the item is still in {@code shapeexcavator:shape_tools};
 * Apply also needs a shape that is valid under the server limits. Opening the editor never writes to
 * the tool, so a tool without a shape stays a vanilla stack until the first accepted Apply. All
 * methods run on the server main thread.
 */
public final class EditSessions {
    /** Minimum ticks between two apply or remove requests of one player. */
    static final int MIN_TICKS_BETWEEN_APPLIES = 5;
    /** Begin requests per player and window; reopening the editor a few times stays allowed. */
    static final int MAX_BEGINS_PER_WINDOW = 4;
    static final int BEGIN_WINDOW_TICKS = 40;

    private record Session(int id, int slot, ItemStack stack, UUID toolId) {
        Session withToolId(UUID newToolId) {
            return new Session(id, slot, stack, newToolId);
        }
    }

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static final Map<UUID, Integer> LAST_APPLY_TICK = new HashMap<>();
    private static final RequestThrottle BEGINS = new RequestThrottle(MAX_BEGINS_PER_WINDOW, BEGIN_WINDOW_TICKS);

    private EditSessions() {
    }

    static void begin(ServerPlayer player, int requestId, int slot) {
        // Flooding is dropped before any session or reply is created; the client asks again after
        // SessionHandshake.RETRY_TICKS, which is longer than the window.
        if (!BEGINS.tryAcquire(player.getUUID(), player.server.getTickCount())) {
            return;
        }
        ShapeLimits limits = ServerConfig.limits();
        ItemStack stack = toolInSelectedSlot(player, slot);
        if (stack == null) {
            SESSIONS.remove(player.getUUID());
            PacketDistributor.sendToPlayer(player, new EditSessionPayload(requestId, 0, false, 0L, limits.maxAxis(),
                    limits.maxSelected(), reason("not_holding_tool")));
            return;
        }
        ToolShapeData data = stack.get(ModComponents.TOOL_SHAPE.get());
        int id = ThreadLocalRandom.current().nextInt(1, Integer.MAX_VALUE);
        SESSIONS.put(player.getUUID(), new Session(id, slot, stack, data == null ? null : data.toolId()));
        PacketDistributor.sendToPlayer(player, new EditSessionPayload(requestId, id, true,
                data == null ? 0L : data.revision(), limits.maxAxis(), limits.maxSelected(), Component.empty()));
    }

    static void apply(ServerPlayer player, ApplyShapePayload request) {
        Session session = openSession(player, request.sessionId(), request.expectedRevision());
        if (session == null) {
            return;
        }
        ItemStack stack = boundTool(player, session, request.sessionId(), request.slot(), request.expectedRevision());
        if (stack == null) {
            return;
        }
        ToolShapeData data = stack.get(ModComponents.TOOL_SHAPE.get());
        long current = data == null ? 0L : data.revision();
        ShapeGenerator.Result result = ShapeGenerator.generate(request.config(), ServerConfig.limits());
        if (result instanceof ShapeGenerator.Invalid invalid) {
            PacketDistributor.sendToPlayer(player, new ApplyResultPayload(request.sessionId(), false, current,
                    Component.translatable("message.shapeexcavator.reject.invalid",
                            ShapeTexts.issue(invalid.issues().get(0)))));
            return;
        }
        // The tool gets its identity with its first accepted shape.
        ToolShapeData base = data != null ? data : ToolShapeData.fresh();
        long revision = current + 1;
        ShapeConfig accepted = request.config().withRevision(revision);
        stack.set(ModComponents.TOOL_SHAPE.get(), base.withConfig(accepted, revision, request.worldPreview()));
        SESSIONS.put(player.getUUID(), session.withToolId(base.toolId()));
        PacketDistributor.sendToPlayer(player, new ApplyResultPayload(request.sessionId(), true, revision,
                Component.translatable("message.shapeexcavator.applied")));
    }

    static void remove(ServerPlayer player, RemoveShapePayload request) {
        Session session = openSession(player, request.sessionId(), request.expectedRevision());
        if (session == null) {
            return;
        }
        ItemStack stack = boundTool(player, session, request.sessionId(), request.slot(), request.expectedRevision());
        if (stack == null) {
            return;
        }
        // The whole component goes, also a damaged one: the tool is a vanilla stack again.
        stack.remove(ModComponents.TOOL_SHAPE.get());
        SESSIONS.put(player.getUUID(), session.withToolId(null));
        PacketDistributor.sendToPlayer(player, new ApplyResultPayload(request.sessionId(), true, 0L,
                Component.translatable("message.shapeexcavator.removed")));
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

    /** The player's session with that id, within the apply rate; otherwise rejects and returns null. */
    private static Session openSession(ServerPlayer player, int sessionId, long expectedRevision) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || session.id() != sessionId) {
            reject(player, sessionId, expectedRevision, "session");
            return null;
        }
        int now = player.server.getTickCount();
        Integer last = LAST_APPLY_TICK.get(player.getUUID());
        if (last != null && now - last < MIN_TICKS_BETWEEN_APPLIES) {
            reject(player, sessionId, expectedRevision, "rate");
            return null;
        }
        LAST_APPLY_TICK.put(player.getUUID(), now);
        return session;
    }

    /** The bound tool if {@link SessionBinding} accepts the request; otherwise rejects and returns null. */
    private static ItemStack boundTool(ServerPlayer player, Session session, int sessionId, int slot,
            long expectedRevision) {
        Inventory inventory = player.getInventory();
        boolean slotSelected = slot == session.slot() && Inventory.isHotbarSlot(slot) && inventory.selected == slot;
        ItemStack stack = slotSelected ? inventory.getItem(slot) : ItemStack.EMPTY;
        ToolShapeData data = stack.get(ModComponents.TOOL_SHAPE.get());
        SessionBinding binding = SessionBinding.check(slotSelected, stack == session.stack(),
                stack.is(ModTags.SHAPE_TOOLS), session.toolId(), data == null ? null : data.toolId(),
                expectedRevision, data == null ? 0L : data.revision());
        if (binding != SessionBinding.ACCEPT) {
            reject(player, sessionId, expectedRevision, binding.rejectKey());
            return null;
        }
        return stack;
    }

    /** The eligible tool in {@code slot}, if that is the selected hotbar slot; otherwise null. */
    private static ItemStack toolInSelectedSlot(ServerPlayer player, int slot) {
        Inventory inventory = player.getInventory();
        if (!Inventory.isHotbarSlot(slot) || inventory.selected != slot) {
            return null;
        }
        ItemStack stack = inventory.getItem(slot);
        return stack.is(ModTags.SHAPE_TOOLS) ? stack : null;
    }

    private static void reject(ServerPlayer player, int sessionId, long expectedRevision, String reason) {
        PacketDistributor.sendToPlayer(player,
                new ApplyResultPayload(sessionId, false, expectedRevision, reason(reason)));
    }

    private static Component reason(String key) {
        return Component.translatable("message.shapeexcavator.reject." + key);
    }
}
