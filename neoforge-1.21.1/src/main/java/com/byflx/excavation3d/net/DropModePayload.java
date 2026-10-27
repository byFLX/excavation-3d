package com.byflx.excavation3d.net;

import com.byflx.excavation3d.Excavation3D;
import com.byflx.excavation3d.excavation.DropMode;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** One unsigned byte; unknown enum ordinals are rejected before handling. */
public record DropModePayload(DropMode mode) implements CustomPacketPayload {
    public static final Type<DropModePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Excavation3D.MOD_ID, "drop_mode"));
    public static final StreamCodec<FriendlyByteBuf, DropModePayload> STREAM_CODEC =
            CustomPacketPayload.codec(DropModePayload::write, DropModePayload::read);
    private void write(FriendlyByteBuf buf) { buf.writeByte(mode.ordinal()); }
    private static DropModePayload read(FriendlyByteBuf buf) {
        try { return new DropModePayload(DropMode.fromOrdinal(buf.readUnsignedByte())); }
        catch (IllegalArgumentException e) { throw new DecoderException(e); }
    }
    @Override public Type<DropModePayload> type() { return TYPE; }
}
