package com.byflx.excavation3d.net;

import com.byflx.excavation3d.client.ClientPayloadHandler;
import com.byflx.excavation3d.config.ServerConfig;
import com.byflx.excavation3d.excavation.ExcavationService;
import com.byflx.excavation3d.excavation.DropPreferences;
import com.byflx.excavation3d.excavation.DropPolicy;
import com.byflx.excavation3d.shape.ShapeLimits;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Payload registration and the server side of the editor protocol. Server handlers re-check
 * everything; client handlers live in {@link ClientPayloadHandler}, which is only loaded when a
 * client-bound payload arrives, so a dedicated server never loads client classes.
 */
public final class ModNetwork {
    /** "6": per-player drop preference and server delivery policy. */
    private static final String VERSION = "6";
    private static DropPolicy lastPolicy;

    private ModNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(VERSION);
        registrar.playToServer(DropModePayload.TYPE, DropModePayload.STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) DropPreferences.set(player.getUUID(), payload.mode());
                }));
        registrar.playToClient(DropPolicyPayload.TYPE, DropPolicyPayload.STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> ClientPayloadHandler.onDropPolicy(payload)));
        registrar.playToServer(BeginEditPayload.TYPE, BeginEditPayload.STREAM_CODEC, ModNetwork::onBeginEdit);
        registrar.playToServer(ApplyShapePayload.TYPE, ApplyShapePayload.STREAM_CODEC, ModNetwork::onApply);
        registrar.playToServer(SetShapeEnabledPayload.TYPE, SetShapeEnabledPayload.STREAM_CODEC, ModNetwork::onSetEnabled);
        registrar.playToServer(RemoveShapePayload.TYPE, RemoveShapePayload.STREAM_CODEC, ModNetwork::onRemove);
        registrar.playToServer(BypassPayload.TYPE, BypassPayload.STREAM_CODEC, ModNetwork::onBypass);
        registrar.playToClient(EditSessionPayload.TYPE, EditSessionPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientPayloadHandler.onEditSession(payload)));
        registrar.playToClient(ApplyResultPayload.TYPE, ApplyResultPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientPayloadHandler.onApplyResult(payload)));
        registrar.playToClient(LimitsPayload.TYPE, LimitsPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientPayloadHandler.onLimits(payload)));
    }

    private static void onBeginEdit(BeginEditPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                EditSessions.begin(player, payload.requestId(), payload.slot());
            }
        });
    }

    private static void onApply(ApplyShapePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                EditSessions.apply(player, payload);
            }
        });
    }

    private static void onSetEnabled(SetShapeEnabledPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) EditSessions.setEnabled(player, payload);
        });
    }

    private static void onRemove(RemoveShapePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                EditSessions.remove(player, payload);
            }
        });
    }

    private static void onBypass(BypassPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                ExcavationService.setBypass(player, payload.active());
            }
        });
    }

    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        // Connections without the negotiated channel (Game Test players) never get mod payloads.
        if (event.getEntity() instanceof ServerPlayer player && player.connection.hasChannel(LimitsPayload.TYPE)) {
            sendDropPolicy(player);
            ShapeLimits limits = ServerConfig.limits();
            PacketDistributor.sendToPlayer(player, new LimitsPayload(limits.maxAxis(), limits.maxSelected()));
        }
    }

    private static void sendDropPolicy(ServerPlayer player) {
        if (player.connection.hasChannel(DropPolicyPayload.TYPE))
            PacketDistributor.sendToPlayer(player, new DropPolicyPayload(ServerConfig.dropPolicy()));
    }

    /** Config reloads are observed on the server thread and immediately reflected in open editors. */
    public static void onServerTick(ServerTickEvent.Post event) {
        DropPolicy policy = ServerConfig.dropPolicy();
        if (policy == lastPolicy) return;
        lastPolicy = policy;
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) sendDropPolicy(player);
    }

    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        EditSessions.end(event.getEntity().getUUID());
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        EditSessions.clear();
        lastPolicy = null;
    }
}
