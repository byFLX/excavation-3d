package com.byflx.excavation3d.net;

import com.byflx.excavation3d.Excavation3D;
import com.byflx.excavation3d.excavation.DropPolicy;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** One unsigned byte; unknown enum ordinals are rejected before handling. */
public record DropPolicyPayload(DropPolicy policy) implements CustomPacketPayload {
    public static final Type<DropPolicyPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Excavation3D.MOD_ID, "drop_policy"));
    public static final StreamCodec<FriendlyByteBuf, DropPolicyPayload> STREAM_CODEC =
            CustomPacketPayload.codec(DropPolicyPayload::write, DropPolicyPayload::read);
    private void write(FriendlyByteBuf buf) { buf.writeByte(policy.ordinal()); }
    private static DropPolicyPayload read(FriendlyByteBuf buf) {
        try { return new DropPolicyPayload(DropPolicy.fromOrdinal(buf.readUnsignedByte())); }
        catch (IllegalArgumentException e) { throw new DecoderException(e); }
    }
    @Override public Type<DropPolicyPayload> type() { return TYPE; }
}
