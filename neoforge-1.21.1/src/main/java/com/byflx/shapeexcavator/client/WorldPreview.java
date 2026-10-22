package com.byflx.shapeexcavator.client;

import com.byflx.shapeexcavator.data.ToolShapeData;
import com.byflx.shapeexcavator.registry.ModTags;
import com.byflx.shapeexcavator.shape.Offset;
import com.byflx.shapeexcavator.shape.Orientation;
import com.byflx.shapeexcavator.shape.ShapeConfig;
import com.byflx.shapeexcavator.shape.ShapeGenerator;
import com.byflx.shapeexcavator.shape.ShapeLimits;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4fStack;
import org.joml.Quaternionf;

/**
 * The in-world preview at the targeted start block (plan §8). It uses the same generator, the same
 * {@link Orientation} and the same {@code worldOffsets} as the server, so the geometric selection
 * is identical. It only looks: nothing is broken and no break events are fired. Permissions and
 * protection are decided by the server, so the hint labels them as unknown.
 */
public final class WorldPreview {
    public enum Category {
        START(1.0F, 0.85F, 0.2F, 1.0F),
        PRESENT(0.85F, 0.95F, 1.0F, 0.9F),
        AIR(0.6F, 0.6F, 0.6F, 0.35F),
        EXCLUDED(1.0F, 0.3F, 0.3F, 0.9F),
        UNLOADED(1.0F, 0.6F, 0.1F, 0.9F);

        final float red;
        final float green;
        final float blue;
        final float alpha;

        Category(float red, float green, float blue, float alpha) {
            this.red = red;
            this.green = green;
            this.blue = blue;
            this.alpha = alpha;
        }
    }

    private record Entry(BlockPos pos, Category category) {
    }

    private static List<Entry> entries = List.of();
    private static final Map<Category, Integer> COUNTS = new EnumMap<>(Category.class);
    private static ShapeConfig cachedConfig;
    private static ShapeLimits cachedLimits;
    private static ShapeGenerator.Result cachedResult;

    private WorldPreview() {
    }

    /** Whether the preview is currently shown; the counts are only meaningful then. */
    public static boolean active() {
        return !entries.isEmpty();
    }

    public static int count(Category category) {
        return COUNTS.getOrDefault(category, 0);
    }

    static void clear() {
        entries = List.of();
        COUNTS.clear();
        cachedConfig = null;
        cachedLimits = null;
        cachedResult = null;
    }

    /** Recomputes the classified positions once per client tick, not per frame. */
    static void tick(Minecraft minecraft) {
        entries = List.of();
        COUNTS.clear();
        Player player = minecraft.player;
        ClientLevel level = minecraft.level;
        if (player == null || level == null || ClientState.bypassActive()) {
            return;
        }
        ItemStack tool = player.getMainHandItem();
        ToolShapeData data = ModTags.shapeOf(tool);
        if (data == null || !data.worldPreview() || data.config().isEmpty()
                || !(minecraft.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            return;
        }
        ShapeConfig config = data.config().get();
        ShapeLimits limits = ClientState.limits();
        if (!config.equals(cachedConfig) || !limits.equals(cachedLimits)) {
            cachedConfig = config;
            cachedLimits = limits;
            cachedResult = ShapeGenerator.generate(config, limits);
        }
        if (!(cachedResult instanceof ShapeGenerator.Valid valid)) {
            return;
        }
        BlockPos origin = hit.getBlockPos();
        BlockState startState = level.getBlockState(origin);
        if (startState.hasBlockEntity()) {
            // The server never starts an area from storage or machines; show nothing extra.
            return;
        }
        Orientation orientation = Orientation.orient(hit.getDirection(), player.getDirection(),
                Orientation.Mode.forType(config.type()));
        float startProgress = startState.getDestroyProgress(player, level, origin);
        List<Entry> computed = new ArrayList<>(valid.shape().count());
        for (Offset offset : valid.shape().worldOffsets(orientation)) {
            BlockPos pos = origin.offset(offset.x(), offset.y(), offset.z());
            Category category = offset.equals(Offset.ZERO)
                    ? Category.START
                    : classify(level, player, tool, pos, startProgress);
            computed.add(new Entry(pos, category));
            COUNTS.merge(category, 1, Integer::sum);
        }
        entries = computed;
    }

    /** A client-side estimate of the server's checks; protection is unknown here and not guessed. */
    private static Category classify(ClientLevel level, Player player, ItemStack tool, BlockPos pos,
            float startProgress) {
        if (level.isOutsideBuildHeight(pos) || !level.getWorldBorder().isWithinBounds(pos)) {
            return Category.EXCLUDED;
        }
        if (!level.isLoaded(pos)) {
            return Category.UNLOADED;
        }
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            return Category.AIR;
        }
        if (state.hasBlockEntity() || state.getBlock() instanceof LiquidBlock || state.getDestroySpeed(level, pos) < 0) {
            return Category.EXCLUDED;
        }
        if (!player.isCreative()) {
            if (state.requiresCorrectToolForDrops() && !tool.isCorrectToolForDrops(state)) {
                return Category.EXCLUDED;
            }
            if (state.getDestroyProgress(player, level, pos) < startProgress * 0.9999F) {
                return Category.EXCLUDED;
            }
        }
        return Category.PRESENT;
    }

    static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || entries.isEmpty()) {
            return;
        }
        Camera camera = event.getCamera();
        Vec3 cameraPos = camera.getPosition();
        // Draw with an explicit view matrix and an identity model-view, independent of what the
        // render stage left on the global model-view stack.
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.identity();
        RenderSystem.applyModelViewMatrix();
        PoseStack pose = new PoseStack();
        pose.mulPose(camera.rotation().conjugate(new Quaternionf()));
        pose.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        for (Entry entry : entries) {
            Category c = entry.category();
            LevelRenderer.renderLineBox(pose, lines, new AABB(entry.pos()).inflate(0.002), c.red, c.green, c.blue,
                    c.alpha);
        }
        buffers.endBatch(RenderType.lines());

        modelView.popMatrix();
        RenderSystem.applyModelViewMatrix();
    }
}
