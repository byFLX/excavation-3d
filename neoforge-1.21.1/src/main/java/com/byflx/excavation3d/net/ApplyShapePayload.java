package com.byflx.excavation3d.net;

import com.byflx.excavation3d.Excavation3D;
import com.byflx.excavation3d.data.ShapeCodecs;
import com.byflx.excavation3d.shape.ShapeConfig;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client → server: "Apply" (plan §9). The server re-checks session, slot, tool identity, the
 * expected revision and the whole shape before anything changes. Malformed or oversized shape data
 * fails decoding in {@link ShapeCodecs#readConfig}.
 */
public record ApplyShapePayload(int sessionId, int slot, long expectedRevision, ShapeConfig config,
        boolean worldPreview) implements CustomPacketPayload {
    public static final Type<ApplyShapePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Excavation3D.MOD_ID, "apply_shape"));
    public static final StreamCodec<FriendlyByteBuf, ApplyShapePayload> STREAM_CODEC =
            CustomPacketPayload.codec(ApplyShapePayload::write, ApplyShapePayload::read);

    private void write(FriendlyByteBuf buf) {
        buf.writeVarInt(sessionId);
        buf.writeVarInt(slot);
        buf.writeVarLong(expectedRevision);
        ShapeCodecs.writeConfig(buf, config);
        buf.writeBoolean(worldPreview);
    }

    private static ApplyShapePayload read(FriendlyByteBuf buf) {
        int sessionId = buf.readVarInt();
        int slot = buf.readVarInt();
        long expectedRevision = buf.readVarLong();
        ShapeConfig config = ShapeCodecs.readConfig(buf, 0L);
        return new ApplyShapePayload(sessionId, slot, expectedRevision, config, buf.readBoolean());
    }

    @Override
    public Type<ApplyShapePayload> type() {
        return TYPE;
    }
}
