package com.byflx.excavation3d.net;

import com.byflx.excavation3d.Excavation3D;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Pause/resume request bound to the same session, stack and revision as shape application. */
public record SetShapeEnabledPayload(int sessionId, int slot, long expectedRevision, boolean enabled) implements CustomPacketPayload {
    public static final Type<SetShapeEnabledPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Excavation3D.MOD_ID, "set_shape_enabled"));
    public static final StreamCodec<FriendlyByteBuf, SetShapeEnabledPayload> STREAM_CODEC =
            CustomPacketPayload.codec(SetShapeEnabledPayload::write, SetShapeEnabledPayload::read);

    private void write(FriendlyByteBuf buf) {
        buf.writeVarInt(sessionId);
        buf.writeVarInt(slot);
        buf.writeVarLong(expectedRevision);
        buf.writeBoolean(enabled);
    }

    private static SetShapeEnabledPayload read(FriendlyByteBuf buf) {
        return new SetShapeEnabledPayload(buf.readVarInt(), buf.readVarInt(), buf.readVarLong(), buf.readBoolean());
    }

    @Override
    public Type<SetShapeEnabledPayload> type() {
        return TYPE;
    }
}
