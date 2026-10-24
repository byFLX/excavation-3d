package com.byflx.excavation3d.client;

import com.byflx.excavation3d.data.ToolShapeData;
import com.byflx.excavation3d.editor.SessionHandshake;
import com.byflx.excavation3d.net.ApplyResultPayload;
import com.byflx.excavation3d.net.ApplyShapePayload;
import com.byflx.excavation3d.net.BeginEditPayload;
import com.byflx.excavation3d.net.EditSessionPayload;
import com.byflx.excavation3d.net.SetShapeEnabledPayload;
import com.byflx.excavation3d.registry.ModComponents;
import com.byflx.excavation3d.registry.ModTags;
import com.byflx.excavation3d.shape.ShapeConfig;
import com.byflx.excavation3d.shape.ShapeGenerator;
import com.byflx.excavation3d.text.ShapeTexts;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/** Quick selection uses the editor's existing Begin -> session -> Apply/SetEnabled -> confirmation protocol. */
public final class QuickShapeAction {
    private static QuickShapeAction pending;
    private record PresetSelection(UUID player, int slot, Object item, String name, ShapeConfig config) { }
    private static PresetSelection confirmedPreset;
    private final SessionHandshake handshake = new SessionHandshake();
    private final int slot;
    private final ItemStack stack;
    private final ShapeConfig config;
    private final ToolShapeData previous;
    private final String presetName;
    private final Boolean enabledTarget;
    private int replyTicks;
    private boolean submitted;

    private QuickShapeAction(Minecraft mc, ShapeConfig config, String presetName, Boolean enabledTarget) {
        slot = mc.player.getInventory().selected;
        stack = mc.player.getMainHandItem();
        previous = stack.get(ModComponents.TOOL_SHAPE.get());
        this.config = config;
        this.presetName = presetName;
        this.enabledTarget = enabledTarget;
    }

    public static boolean busy() { return pending != null; }

    /** The exact accepted revision prevents a cursor from following an editor change or another tool. */
    static String confirmedPresetName() {
        Minecraft mc = Minecraft.getInstance();
        if (confirmedPreset == null || mc.player == null) return null;
        ToolShapeData data = ModTags.shapeOf(mc.player.getMainHandItem());
        return confirmedPreset.player().equals(mc.player.getUUID())
                && confirmedPreset.slot() == mc.player.getInventory().selected
                && confirmedPreset.item() == mc.player.getMainHandItem().getItem()
                && data != null && data.config().filter(confirmedPreset.config()::equals).isPresent()
                ? confirmedPreset.name() : null;
    }

    public static void startPreset(ShapeConfig config, String name) {
        start(config, name);
    }

    /** Client preference: no edit session or server packet. */
    public static void toggleWorldPreview() {
        boolean enabled = com.byflx.excavation3d.config.ClientConfig.toggleWorldPreview();
        ToolHud.feedback(Component.translatable(enabled
                ? "tool.excavation3d.world_preview.on" : "tool.excavation3d.world_preview.off")
                .withStyle(ChatFormatting.GRAY));
    }

    /** Null config toggles enabled. No local item component is ever changed. */
    public static void start(ShapeConfig config) {
        start(config, null);
    }

    private static void start(ShapeConfig config, String presetName) {
        start(config, presetName, null);
    }

    /** Wheel Off/Resume are idempotent; the separate toggle binding still uses start(null). */
    public static void setEnabled(boolean enabled) {
        start(null, null, enabled);
    }

    private static void start(ShapeConfig config, String presetName, Boolean enabledTarget) {
        Minecraft mc = Minecraft.getInstance();
        if (busy() || mc.screen != null || mc.player == null || mc.getConnection() == null
                || !mc.player.getMainHandItem().is(ModTags.SHAPE_TOOLS)) return;
        if (config == null) {
            ToolShapeData data = ModTags.shapeOf(mc.player.getMainHandItem());
            if (data == null || data.config().isEmpty()) return;
            if (enabledTarget != null && data.enabled() == enabledTarget) return;
        }
        pending = new QuickShapeAction(mc, config, presetName, enabledTarget);
        PacketDistributor.sendToServer(new BeginEditPayload(pending.handshake.start(), pending.slot));
    }

    public static void tick() {
        if (pending == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) { pending = null; return; }
        if (!pending.submitted && (mc.player.getInventory().selected != pending.slot
                || mc.player.getMainHandItem() != pending.stack)) {
            finish(Component.translatable("message.excavation3d.reject.tool_changed"));
            return;
        }
        int retry = pending.handshake.tick();
        if (retry != 0) PacketDistributor.sendToServer(new BeginEditPayload(retry, pending.slot));
        if (pending.handshake.state() == SessionHandshake.State.NO_REPLY
                || (pending.submitted && ++pending.replyTicks >= 200)) {
            finish(Component.translatable("gui.excavation3d.status.no_reply"), ChatFormatting.YELLOW);
        }
    }

    public static void onSession(EditSessionPayload payload) {
        if (pending == null || !pending.handshake.onReply(payload.requestId(), payload.ok(), payload.sessionId())) return;
        if (pending.handshake.state() != SessionHandshake.State.OPEN) { finish(payload.message()); return; }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.getInventory().selected != pending.slot
                || mc.player.getMainHandItem() != pending.stack) {
            finish(Component.translatable("message.excavation3d.reject.tool_changed"));
            return;
        }
        // Toggling must target the exact revision observed before opening the session.
        long expectedRevision = pending.previous == null ? 0L : pending.previous.revision();
        if (payload.revision() != expectedRevision) {
            finish(Component.translatable("message.excavation3d.reject.revision"));
            return;
        }
        if (pending.config != null && ShapeGenerator.generate(pending.config, ClientState.limits())
                instanceof ShapeGenerator.Invalid invalid) {
            finish(Component.translatable("message.excavation3d.reject.invalid", ShapeTexts.issue(invalid.issues().getFirst())));
            return;
        }
        pending.submitted = true;
        if (pending.config == null) {
            PacketDistributor.sendToServer(new SetShapeEnabledPayload(payload.sessionId(), pending.slot,
                    payload.revision(), pending.enabledTarget != null ? pending.enabledTarget : !pending.previous.enabled()));
        } else PacketDistributor.sendToServer(new ApplyShapePayload(payload.sessionId(), pending.slot, payload.revision(),
                    pending.config));
    }

    public static void onResult(ApplyResultPayload payload) {
        if (pending == null || !pending.submitted || payload.sessionId() != pending.handshake.sessionId()) return;
        if (payload.accepted()) {
            Minecraft mc = Minecraft.getInstance();
            confirmedPreset = pending.presetName != null && mc.player != null
                    ? new PresetSelection(mc.player.getUUID(), pending.slot, pending.stack.getItem(),
                            pending.presetName, pending.config.withRevision(payload.revision())) : null;
            ToolHud.confirmedChange();
            pending = null;
            return;
        }
        finish(payload.message());
    }

    private static void finish(Component message) {
        finish(message, ChatFormatting.RED);
    }

    private static void finish(Component message, ChatFormatting color) {
        ToolHud.feedback(message.copy().withStyle(color));
        pending = null;
    }

    public static void reset() { pending = null; confirmedPreset = null; }
}
