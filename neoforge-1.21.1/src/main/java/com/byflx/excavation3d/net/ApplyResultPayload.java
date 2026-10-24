package com.byflx.excavation3d.net;

import com.byflx.excavation3d.Excavation3D;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server → client: whether an {@link ApplyShapePayload} was accepted, the new revision and why not. */
public record ApplyResultPayload(int sessionId, boolean accepted, long revision, Component message)
        implements CustomPacketPayload {
    public static final Type<ApplyResultPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Excavation3D.MOD_ID, "apply_result"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ApplyResultPayload> STREAM_CODEC =
            CustomPacketPayload.codec(ApplyResultPayload::write, ApplyResultPayload::read);

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(sessionId);
        buf.writeBoolean(accepted);
        buf.writeVarLong(revision);
        ComponentSerialization.STREAM_CODEC.encode(buf, message);
    }

    private static ApplyResultPayload read(RegistryFriendlyByteBuf buf) {
        return new ApplyResultPayload(buf.readVarInt(), buf.readBoolean(), buf.readVarLong(),
                ComponentSerialization.STREAM_CODEC.decode(buf));
    }

    @Override
    public Type<ApplyResultPayload> type() {
        return TYPE;
    }
}
