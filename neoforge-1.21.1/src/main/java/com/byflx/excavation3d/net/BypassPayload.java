package com.byflx.excavation3d.net;

import com.byflx.excavation3d.Excavation3D;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client → server: the single-block key is held or released (plan §2). Sent only on change. */
public record BypassPayload(boolean active) implements CustomPacketPayload {
    public static final Type<BypassPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Excavation3D.MOD_ID, "single_block"));
    public static final StreamCodec<FriendlyByteBuf, BypassPayload> STREAM_CODEC =
            CustomPacketPayload.codec(BypassPayload::write, BypassPayload::read);

    private void write(FriendlyByteBuf buf) {
        buf.writeBoolean(active);
    }

    private static BypassPayload read(FriendlyByteBuf buf) {
        return new BypassPayload(buf.readBoolean());
    }

    @Override
    public Type<BypassPayload> type() {
        return TYPE;
    }
}
