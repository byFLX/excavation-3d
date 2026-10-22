package com.byflx.shapeexcavator.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.byflx.shapeexcavator.shape.Cell;
import com.byflx.shapeexcavator.shape.ShapeConfig;
import com.byflx.shapeexcavator.shape.ShapeMask;
import com.byflx.shapeexcavator.shape.ShapeParams;
import com.byflx.shapeexcavator.shape.ShapeTransform;
import com.byflx.shapeexcavator.shape.ShapeType;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

class ShapeCodecsTest {
    private static final UUID TOOL = UUID.fromString("00000000-0000-0000-0000-000000000042");

    private static ShapeConfig custom() {
        boolean[][][] cells = new boolean[3][3][2];
        cells[1][0][0] = true;
        cells[1][1][0] = true;
        cells[0][1][1] = true;
        return new ShapeConfig(ShapeConfig.FORMAT_VERSION, new ShapeParams.Custom(ShapeMask.of(cells, new Cell(1, 0, 0))),
                new ShapeTransform(3, true), 0L);
    }

    private static ToolShapeData jsonRoundTrip(ToolShapeData data) {
        JsonElement json = ToolShapeData.CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow();
        return ToolShapeData.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
    }

    @Test
    void componentSurvivesPersistence() {
        ToolShapeData data = new ToolShapeData(TOOL, 5L, Optional.of(custom()), true);
        ToolShapeData restored = jsonRoundTrip(data);
        assertEquals(data, restored);
        assertEquals(5L, restored.config().orElseThrow().revision());
    }

    @Test
    void componentSurvivesNetworkSync() {
        for (ToolShapeData data : new ToolShapeData[] {
                new ToolShapeData(TOOL, 5L, Optional.of(custom()), true),
                new ToolShapeData(TOOL, 1L, Optional.of(ShapeConfig.of(new ShapeParams.Plus(5, 5, 2, 1))), false),
                ToolShapeData.fresh()}) {
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            ToolShapeData.STREAM_CODEC.encode(buf, data);
            assertEquals(data, ToolShapeData.STREAM_CODEC.decode(buf));
            assertEquals(0, buf.readableBytes());
        }
    }

    @Test
    void twoToolsStayIndependent() {
        ToolShapeData a = ToolShapeData.fresh();
        ToolShapeData b = ToolShapeData.fresh();
        assertFalse(a.toolId().equals(b.toolId()));
        ToolShapeData configured = a.withConfig(ShapeConfig.of(new ShapeParams.Cuboid(3, 3, 1)), 1L, false);
        assertEquals(a.toolId(), configured.toolId());
        assertTrue(b.config().isEmpty());
    }

    @Test
    void brokenStoredShapeIsDroppedNotTheItem() {
        ToolShapeData data = new ToolShapeData(TOOL, 3L, Optional.of(custom()), false);
        JsonObject json = ToolShapeData.CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow().getAsJsonObject();
        json.getAsJsonObject("shape").addProperty("type", "dodecahedron");
        ToolShapeData restored = ToolShapeData.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertEquals(TOOL, restored.toolId());
        assertEquals(3L, restored.revision());
        assertTrue(restored.config().isEmpty());
        assertTrue(restored.isDamaged(), "a once-configured tool with unreadable data must report damage");
        assertFalse(ToolShapeData.fresh().isDamaged());
    }

    @Test
    void unknownFutureFormatVersionIsDroppedAsDamaged() {
        JsonElement json = JsonParser.parseString("""
                {"tool_id":[0,0,0,66],"revision":2,
                 "shape":{"format":2,"type":"cuboid","values":[3,3,1]}}""");
        ToolShapeData restored = ToolShapeData.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertTrue(restored.isDamaged());
    }

    @Test
    void oversizedNetworkMaskIsRejectedBeforeAllocation() {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeVarInt(ShapeConfig.FORMAT_VERSION);
        buf.writeByte(ShapeType.CUSTOM.ordinal());
        buf.writeByte(6);
        for (int v : new int[] {32, 32, 32, 0, 0, 0}) {
            buf.writeInt(v);
        }
        buf.writeVarInt(Integer.MAX_VALUE);
        assertThrows(DecoderException.class, () -> ShapeCodecs.readConfig(buf, 0L));
    }

    @Test
    void malformedNetworkShapesAreRejected() {
        FriendlyByteBuf unknownType = new FriendlyByteBuf(Unpooled.buffer());
        unknownType.writeVarInt(ShapeConfig.FORMAT_VERSION);
        unknownType.writeByte(200);
        assertThrows(DecoderException.class, () -> ShapeCodecs.readConfig(unknownType, 0L));

        FriendlyByteBuf tooManyValues = new FriendlyByteBuf(Unpooled.buffer());
        tooManyValues.writeVarInt(ShapeConfig.FORMAT_VERSION);
        tooManyValues.writeByte(ShapeType.CUBOID.ordinal());
        tooManyValues.writeByte(255);
        assertThrows(DecoderException.class, () -> ShapeCodecs.readConfig(tooManyValues, 0L));

        FriendlyByteBuf badRotation = new FriendlyByteBuf(Unpooled.buffer());
        ShapeCodecs.writeConfig(badRotation, ShapeConfig.of(new ShapeParams.Cuboid(1, 1, 1)));
        badRotation.setByte(badRotation.writerIndex() - 2, 7);
        assertThrows(DecoderException.class, () -> ShapeCodecs.readConfig(badRotation, 0L));
    }

    @Test
    void networkShapeRoundTrips() {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        ShapeConfig config = custom();
        ShapeCodecs.writeConfig(buf, config);
        assertEquals(config.withRevision(4L), ShapeCodecs.readConfig(buf, 4L));
    }
}
