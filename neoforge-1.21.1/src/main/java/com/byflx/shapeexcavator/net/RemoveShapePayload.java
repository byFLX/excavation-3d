package com.byflx.shapeexcavator.net;

import com.byflx.shapeexcavator.ShapeExcavator;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client → server: "Remove shape" (plan §2a). The server applies the same session, slot, stack,
 * tag and revision checks as for {@link ApplyShapePayload} and then deletes the whole component, so
 * the tool mines like vanilla again. Answered with an {@link ApplyResultPayload} at revision 0.
 */
public record RemoveShapePayload(int sessionId, int slot, long expectedRevision) implements CustomPacketPayload {
    public static final Type<RemoveShapePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ShapeExcavator.MOD_ID, "remove_shape"));
    public static final StreamCodec<FriendlyByteBuf, RemoveShapePayload> STREAM_CODEC =
            CustomPacketPayload.codec(RemoveShapePayload::write, RemoveShapePayload::read);

    private void write(FriendlyByteBuf buf) {
        buf.writeVarInt(sessionId);
        buf.writeVarInt(slot);
        buf.writeVarLong(expectedRevision);
    }

    private static RemoveShapePayload read(FriendlyByteBuf buf) {
        return new RemoveShapePayload(buf.readVarInt(), buf.readVarInt(), buf.readVarLong());
    }

    @Override
    public Type<RemoveShapePayload> type() {
        return TYPE;
    }
}
