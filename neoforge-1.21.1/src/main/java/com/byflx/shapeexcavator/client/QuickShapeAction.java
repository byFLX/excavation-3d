package com.byflx.shapeexcavator.client;

import com.byflx.shapeexcavator.data.ToolShapeData;
import com.byflx.shapeexcavator.editor.SessionHandshake;
import com.byflx.shapeexcavator.net.ApplyResultPayload;
import com.byflx.shapeexcavator.net.ApplyShapePayload;
import com.byflx.shapeexcavator.net.BeginEditPayload;
import com.byflx.shapeexcavator.net.EditSessionPayload;
import com.byflx.shapeexcavator.net.RemoveShapePayload;
import com.byflx.shapeexcavator.registry.ModComponents;
import com.byflx.shapeexcavator.registry.ModTags;
import com.byflx.shapeexcavator.shape.ShapeConfig;
import com.byflx.shapeexcavator.shape.ShapeGenerator;
import com.byflx.shapeexcavator.text.ShapeTexts;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/** Quick selection uses the editor's existing Begin -> session -> Apply/Remove -> confirmation protocol. */
public final class QuickShapeAction {
    private static QuickShapeAction pending;
    private static int feedbackUntilGuiTick;
    private final SessionHandshake handshake = new SessionHandshake();
    private final int slot;
    private final ItemStack stack;
    private final ShapeConfig config;
    private final ToolShapeData previous;
    private final boolean worldPreview;
    private final Component selectionName;
    private int replyTicks;
    private boolean submitted;

    private QuickShapeAction(Minecraft mc, ShapeConfig config, boolean worldPreview, Component selectionName) {
        slot = mc.player.getInventory().selected;
        stack = mc.player.getMainHandItem();
        previous = stack.get(ModComponents.TOOL_SHAPE.get());
        this.config = config;
        this.worldPreview = worldPreview;
        this.selectionName = selectionName.copy();
    }

    public static boolean busy() { return pending != null; }

    /** Vanilla action-bar messages last 60 GUI ticks; leave that space clear for the whole result. */
    static boolean showingFeedback() {
        return Minecraft.getInstance().gui.getGuiTicks() < feedbackUntilGuiTick;
    }

    /** Null config means Off. No local item component is ever changed. */
    public static void start(ShapeConfig config, boolean worldPreview, Component selectionName) {
        Minecraft mc = Minecraft.getInstance();
        if (busy() || mc.player == null || !mc.player.getMainHandItem().is(ModTags.SHAPE_TOOLS)) return;
        pending = new QuickShapeAction(mc, config, worldPreview, selectionName);
        PacketDistributor.sendToServer(new BeginEditPayload(pending.handshake.start(), pending.slot));
    }

    public static void tick() {
        if (pending == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) { pending = null; return; }
        if (!pending.submitted && (mc.player.getInventory().selected != pending.slot
                || mc.player.getMainHandItem() != pending.stack)) {
            finish(Component.translatable("message.shapeexcavator.reject.tool_changed"));
            return;
        }
        int retry = pending.handshake.tick();
        if (retry != 0) PacketDistributor.sendToServer(new BeginEditPayload(retry, pending.slot));
        if (pending.handshake.state() == SessionHandshake.State.NO_REPLY
                || (pending.submitted && ++pending.replyTicks >= 200)) {
            finish(Component.translatable("gui.shapeexcavator.status.no_reply"));
        }
    }

    public static void onSession(EditSessionPayload payload) {
        if (pending == null || !pending.handshake.onReply(payload.requestId(), payload.ok(), payload.sessionId())) return;
        if (pending.handshake.state() != SessionHandshake.State.OPEN) { finish(payload.message()); return; }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.getInventory().selected != pending.slot
                || mc.player.getMainHandItem() != pending.stack) {
            finish(Component.translatable("message.shapeexcavator.reject.tool_changed"));
            return;
        }
        // In particular, Off must remember the exact revision that it is about to remove.
        long expectedRevision = pending.previous == null ? 0L : pending.previous.revision();
        if (payload.revision() != expectedRevision) {
            finish(Component.translatable("message.shapeexcavator.reject.revision"));
            return;
        }
        if (pending.config != null && ShapeGenerator.generate(pending.config, ClientState.limits())
                instanceof ShapeGenerator.Invalid invalid) {
            finish(Component.translatable("message.shapeexcavator.reject.invalid", ShapeTexts.issue(invalid.issues().getFirst())));
            return;
        }
        pending.submitted = true;
        if (pending.config == null) {
            if (pending.previous != null) {
                ClientState.expectRemoval(payload.sessionId(), mc.player.getUUID(),
                        pending.previous.config().orElse(null), pending.previous.worldPreview());
            }
            PacketDistributor.sendToServer(new RemoveShapePayload(payload.sessionId(), pending.slot, payload.revision()));
        } else PacketDistributor.sendToServer(new ApplyShapePayload(payload.sessionId(), pending.slot, payload.revision(),
                    pending.config, pending.worldPreview));
    }

    public static void onResult(ApplyResultPayload payload) {
        if (pending == null || !pending.submitted || payload.sessionId() != pending.handshake.sessionId()) return;
        finish(payload.accepted()
                ? (pending.config == null ? Component.translatable("gui.shapeexcavator.quick.applied_off")
                        : Component.translatable("gui.shapeexcavator.quick.applied", pending.selectionName))
                : payload.message());
    }

    private static void finish(Component message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(message, true);
            feedbackUntilGuiTick = mc.gui.getGuiTicks() + 60;
        }
        pending = null;
    }

    public static void reset() { pending = null; feedbackUntilGuiTick = 0; }
}
