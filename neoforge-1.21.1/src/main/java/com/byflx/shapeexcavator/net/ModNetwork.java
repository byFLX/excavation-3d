package com.byflx.shapeexcavator.net;

import com.byflx.shapeexcavator.client.ClientPayloadHandler;
import com.byflx.shapeexcavator.config.ServerConfig;
import com.byflx.shapeexcavator.excavation.ExcavationService;
import com.byflx.shapeexcavator.shape.ShapeLimits;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
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
    private static final String VERSION = "1";

    private ModNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(VERSION);
        registrar.playToServer(BeginEditPayload.TYPE, BeginEditPayload.STREAM_CODEC, ModNetwork::onBeginEdit);
        registrar.playToServer(ApplyShapePayload.TYPE, ApplyShapePayload.STREAM_CODEC, ModNetwork::onApply);
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
                EditSessions.begin(player, payload.slot());
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

    private static void onBypass(BypassPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                ExcavationService.setBypass(player, payload.active());
            }
        });
    }

    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ShapeLimits limits = ServerConfig.limits();
            PacketDistributor.sendToPlayer(player, new LimitsPayload(limits.maxAxis(), limits.maxSelected()));
        }
    }

    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        EditSessions.end(event.getEntity().getUUID());
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        EditSessions.clear();
    }
}
