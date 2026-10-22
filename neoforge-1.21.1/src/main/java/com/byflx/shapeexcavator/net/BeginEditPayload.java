package com.byflx.shapeexcavator.net;

import com.byflx.shapeexcavator.ShapeExcavator;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client → server: the editor opened for the tool in hotbar {@code slot}; asks for a session. The
 * server echoes {@code requestId} in its {@link EditSessionPayload}, so the client can tell which
 * request a reply answers.
 */
public record BeginEditPayload(int requestId, int slot) implements CustomPacketPayload {
    public static final Type<BeginEditPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ShapeExcavator.MOD_ID, "begin_edit"));
    public static final StreamCodec<FriendlyByteBuf, BeginEditPayload> STREAM_CODEC =
            CustomPacketPayload.codec(BeginEditPayload::write, BeginEditPayload::read);

    private void write(FriendlyByteBuf buf) {
        buf.writeVarInt(requestId);
        buf.writeVarInt(slot);
    }

    private static BeginEditPayload read(FriendlyByteBuf buf) {
        return new BeginEditPayload(buf.readVarInt(), buf.readVarInt());
    }

    @Override
    public Type<BeginEditPayload> type() {
        return TYPE;
    }
}
