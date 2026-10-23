package com.byflx.excavation3d.data;

import com.byflx.excavation3d.shape.ShapeConfig;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * The active configuration stored on one tool stack as a persistent, synchronised data component
 * (plan §9). Immutable; a change replaces the whole value.
 *
 * @param toolId       identifies this tool, so an editor session cannot write to a swapped tool
 * @param revision     incremented by the server on every accepted change; 0 means never configured
 * @param config       the active shape; empty if never configured or if the stored data was broken
 * @param worldPreview whether the world preview is shown while holding the tool
 */
public record ToolShapeData(UUID toolId, long revision, Optional<ShapeConfig> config, boolean worldPreview) {
    /** A shape that fails to decode is dropped (not the whole item), which {@link #isDamaged()} reports. */
    public static final Codec<ToolShapeData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("tool_id").forGetter(ToolShapeData::toolId),
            Codec.LONG.fieldOf("revision").forGetter(ToolShapeData::revision),
            ShapeCodecs.CONFIG_CODEC.lenientOptionalFieldOf("shape").forGetter(ToolShapeData::config),
            Codec.BOOL.optionalFieldOf("world_preview", false).forGetter(ToolShapeData::worldPreview)
    ).apply(instance, ToolShapeData::new));

    public static final StreamCodec<FriendlyByteBuf, ToolShapeData> STREAM_CODEC =
            StreamCodec.ofMember(ToolShapeData::write, ToolShapeData::read);

    public ToolShapeData {
        Objects.requireNonNull(toolId, "toolId");
        Objects.requireNonNull(config, "config");
        // The stored revision is authoritative; keep the config's copy equal so equality is by value.
        long rev = revision;
        config = config.map(c -> c.revision() == rev ? c : c.withRevision(rev));
    }

    /** A tool that has an identity but no configuration yet. */
    public static ToolShapeData fresh() {
        return new ToolShapeData(UUID.randomUUID(), 0L, Optional.empty(), false);
    }

    /** True when a configuration was accepted once but can no longer be read. */
    public boolean isDamaged() {
        return revision > 0 && config.isEmpty();
    }

    public ToolShapeData withConfig(ShapeConfig newConfig, long newRevision, boolean newWorldPreview) {
        return new ToolShapeData(toolId, newRevision, Optional.of(newConfig), newWorldPreview);
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeUUID(toolId);
        buf.writeVarLong(revision);
        buf.writeBoolean(worldPreview);
        buf.writeBoolean(config.isPresent());
        config.ifPresent(c -> ShapeCodecs.writeConfig(buf, c));
    }

    private static ToolShapeData read(FriendlyByteBuf buf) {
        UUID toolId = buf.readUUID();
        long revision = buf.readVarLong();
        boolean worldPreview = buf.readBoolean();
        Optional<ShapeConfig> config = buf.readBoolean()
                ? Optional.of(ShapeCodecs.readConfig(buf, revision))
                : Optional.empty();
        return new ToolShapeData(toolId, revision, config, worldPreview);
    }
}
