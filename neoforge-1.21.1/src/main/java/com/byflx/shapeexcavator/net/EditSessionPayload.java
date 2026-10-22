package com.byflx.shapeexcavator.net;

import com.byflx.shapeexcavator.ShapeExcavator;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server → client: answer to {@link BeginEditPayload}. On success it carries the session id, the
 * tool's current revision and the effective server limits; otherwise {@code message} says why.
 */
public record EditSessionPayload(int sessionId, boolean ok, long revision, int maxAxis, int maxSelected,
        Component message) implements CustomPacketPayload {
    public static final Type<EditSessionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ShapeExcavator.MOD_ID, "edit_session"));
    public static final StreamCodec<RegistryFriendlyByteBuf, EditSessionPayload> STREAM_CODEC =
            CustomPacketPayload.codec(EditSessionPayload::write, EditSessionPayload::read);

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(sessionId);
        buf.writeBoolean(ok);
        buf.writeVarLong(revision);
        buf.writeVarInt(maxAxis);
        buf.writeVarInt(maxSelected);
        ComponentSerialization.STREAM_CODEC.encode(buf, message);
    }

    private static EditSessionPayload read(RegistryFriendlyByteBuf buf) {
        return new EditSessionPayload(buf.readVarInt(), buf.readBoolean(), buf.readVarLong(), buf.readVarInt(),
                buf.readVarInt(), ComponentSerialization.STREAM_CODEC.decode(buf));
    }

    @Override
    public Type<EditSessionPayload> type() {
        return TYPE;
    }
}
