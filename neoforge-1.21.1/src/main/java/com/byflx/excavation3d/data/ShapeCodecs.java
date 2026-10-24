package com.byflx.excavation3d.data;

import com.byflx.excavation3d.shape.EncodedShape;
import com.byflx.excavation3d.shape.ShapeConfig;
import com.byflx.excavation3d.shape.ShapeType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.handler.codec.DecoderException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.network.FriendlyByteBuf;

/**
 * Persistent (DFU) and network forms of {@link ShapeConfig}, both built on {@link EncodedShape}.
 * The network reader checks every count against its bound before allocating anything (plan §9).
 */
public final class ShapeCodecs {
    public static final Codec<ShapeType> TYPE_CODEC =
            Codec.STRING.comapFlatMap(ShapeCodecs::parseType, ShapeCodecs::typeName);

    public static final Codec<EncodedShape> ENCODED_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("format").forGetter(EncodedShape::formatVersion),
            TYPE_CODEC.fieldOf("type").forGetter(EncodedShape::type),
            Codec.INT.listOf().fieldOf("values").forGetter(EncodedShape::values),
            Codec.LONG.listOf().optionalFieldOf("mask", List.of()).forGetter(EncodedShape::maskWords),
            Codec.INT.optionalFieldOf("rotation", 0).forGetter(EncodedShape::rotation),
            Codec.BOOL.optionalFieldOf("mirrored", false).forGetter(EncodedShape::mirrored),
            Codec.BOOL.optionalFieldOf("player_horizontal", false).forGetter(EncodedShape::playerHorizontal)
    ).apply(instance, EncodedShape::new));

    /** Decodes to a config with revision 0; structurally broken data is a codec error, not an exception. */
    public static final Codec<ShapeConfig> CONFIG_CODEC =
            ENCODED_CODEC.comapFlatMap(ShapeCodecs::decodeConfig, EncodedShape::encode);

    private ShapeCodecs() {
    }

    public static String typeName(ShapeType type) {
        return type.name().toLowerCase(Locale.ROOT);
    }

    private static DataResult<ShapeType> parseType(String name) {
        for (ShapeType type : ShapeType.values()) {
            if (typeName(type).equals(name)) {
                return DataResult.success(type);
            }
        }
        return DataResult.error(() -> "Unknown shape type: " + name);
    }

    private static DataResult<ShapeConfig> decodeConfig(EncodedShape encoded) {
        EncodedShape.Decoded decoded = encoded.decode(0L);
        if (decoded instanceof EncodedShape.Ok ok) {
            return DataResult.success(ok.config());
        }
        String message = ((EncodedShape.Error) decoded).message();
        return DataResult.error(() -> message);
    }

    public static void writeConfig(FriendlyByteBuf buf, ShapeConfig config) {
        EncodedShape encoded = EncodedShape.encode(config);
        buf.writeVarInt(encoded.formatVersion());
        buf.writeByte(encoded.type().ordinal());
        buf.writeByte(encoded.values().size());
        for (int value : encoded.values()) {
            buf.writeInt(value);
        }
        buf.writeVarInt(encoded.maskWords().size());
        for (long word : encoded.maskWords()) {
            buf.writeLong(word);
        }
        buf.writeByte(encoded.rotation());
        buf.writeBoolean(encoded.mirrored());
        buf.writeBoolean(encoded.playerHorizontal());
    }

    /** @throws DecoderException for malformed or oversized data */
    public static ShapeConfig readConfig(FriendlyByteBuf buf, long revision) {
        int formatVersion = buf.readVarInt();
        int typeIndex = buf.readUnsignedByte();
        if (typeIndex >= ShapeType.values().length) {
            throw new DecoderException("Unknown shape type index " + typeIndex);
        }
        int valueCount = buf.readUnsignedByte();
        if (valueCount > EncodedShape.MAX_VALUES) {
            throw new DecoderException("Too many shape values: " + valueCount);
        }
        List<Integer> values = new ArrayList<>(valueCount);
        for (int i = 0; i < valueCount; i++) {
            values.add(buf.readInt());
        }
        int wordCount = buf.readVarInt();
        if (wordCount < 0 || wordCount > EncodedShape.MAX_MASK_WORDS) {
            throw new DecoderException("Mask word count out of range: " + wordCount);
        }
        List<Long> words = new ArrayList<>(wordCount);
        for (int i = 0; i < wordCount; i++) {
            words.add(buf.readLong());
        }
        int rotation = buf.readUnsignedByte();
        boolean mirrored = buf.readBoolean();
        boolean playerHorizontal = buf.readBoolean();
        EncodedShape encoded = new EncodedShape(formatVersion, ShapeType.values()[typeIndex], values, words,
                rotation, mirrored, playerHorizontal);
        return switch (encoded.decode(revision)) {
            case EncodedShape.Ok ok -> ok.config();
            case EncodedShape.Error error -> throw new DecoderException("Invalid shape: " + error.message());
        };
    }
}
