package com.byflx.excavation3d.net;

import com.byflx.excavation3d.Excavation3D;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server → client on login: the effective shape limits, so editor and world preview match the server. */
public record LimitsPayload(int maxAxis, int maxSelected) implements CustomPacketPayload {
    public static final Type<LimitsPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Excavation3D.MOD_ID, "limits"));
    public static final StreamCodec<FriendlyByteBuf, LimitsPayload> STREAM_CODEC =
            CustomPacketPayload.codec(LimitsPayload::write, LimitsPayload::read);

    private void write(FriendlyByteBuf buf) {
        buf.writeVarInt(maxAxis);
        buf.writeVarInt(maxSelected);
    }

    private static LimitsPayload read(FriendlyByteBuf buf) {
        return new LimitsPayload(buf.readVarInt(), buf.readVarInt());
    }

    @Override
    public Type<LimitsPayload> type() {
        return TYPE;
    }
}
