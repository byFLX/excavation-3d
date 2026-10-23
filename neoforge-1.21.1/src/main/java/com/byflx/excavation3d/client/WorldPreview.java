package com.byflx.excavation3d.client;

import com.byflx.excavation3d.data.ToolShapeData;
import com.byflx.excavation3d.preview.CellCategory;
import com.byflx.excavation3d.preview.ContourEdges;
import com.byflx.excavation3d.preview.PreviewFill;
import com.byflx.excavation3d.registry.ModTags;
import com.byflx.excavation3d.shape.Offset;
import com.byflx.excavation3d.shape.Orientation;
import com.byflx.excavation3d.shape.ShapeConfig;
import com.byflx.excavation3d.shape.ShapeGenerator;
import com.byflx.excavation3d.shape.ShapeLimits;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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

/**
 * The in-world preview at the targeted start block (plan §8). It uses the same generator, the same
 * {@link Orientation} and the same {@code worldOffsets} as the server, so the geometric selection
 * is identical. It only looks: nothing is broken and no break events are fired. Permissions and
 * protection are decided by the server, so the hint labels them as unknown.
 *
 * <p>Drawn calmly (request 2026-10-22): the outer contour of the blocks expected to be mined, a
 * light fill on their outer faces and the start block in yellow. Air, excluded and unloaded
 * positions get neither an outline nor a fill.
 */
public final class WorldPreview {
    private static final float[] CONTOUR = {0.92F, 0.96F, 1.0F, 0.85F};
    private static final float[] FILL = {0.55F, 0.85F, 1.0F, 0.18F};
    private static final float[] START_LINE = {1.0F, 0.85F, 0.2F, 1.0F};
    private static final float[] START_FILL = {1.0F, 0.85F, 0.2F, 0.32F};
    /** Lifts the fill off the block faces, so it does not flicker against them. */
    private static final double FACE_LIFT = 0.002;

    /** Everything the classified positions depend on besides the world's blocks. */
    private record Target(ShapeConfig config, ShapeLimits limits, BlockPos origin, Direction face, Direction facing) {
    }

    private static final Map<CellCategory, Integer> COUNTS = new EnumMap<>(CellCategory.class);
    private static ShapeConfig cachedConfig;
    private static ShapeLimits cachedLimits;
    private static ShapeGenerator.Result cachedResult;
    private static Target target;
    private static BlockPos origin;
    private static List<ContourEdges.Edge> contour = List.of();
    private static PreviewFill.Result fill = new PreviewFill.Result(List.of(), Optional.empty(), 0);

    private WorldPreview() {
    }

    /** Whether the preview is currently computed; the counts are only meaningful then. */
    public static boolean active() {
        return origin != null;
    }

    public static int count(CellCategory category) {
        return COUNTS.getOrDefault(category, 0);
    }

    /** Blocks expected to be mined: present blocks plus the start block. */
    public static int minedCount() {
        return fill.count();
    }

    static void clear() {
        clearGeometry();
        cachedConfig = null;
        cachedLimits = null;
        cachedResult = null;
    }

    private static void clearGeometry() {
        COUNTS.clear();
        target = null;
        origin = null;
        contour = List.of();
        fill = new PreviewFill.Result(List.of(), Optional.empty(), 0);
    }

    /** Reclassifies once per client tick, so changed blocks show up without per-frame world scans. */
    static void tick(Minecraft minecraft) {
        update(minecraft, true);
    }

    /**
     * Recomputes the positions when the target or the shape changed, or always when
     * {@code reclassify} is set. The render path calls this every frame with {@code false}: the
     * targeted block is picked per frame, so the preview follows it in the same frame instead of
     * lagging up to a tick behind while looking around.
     */
    private static void update(Minecraft minecraft, boolean reclassify) {
        Player player = minecraft.player;
        ClientLevel level = minecraft.level;
        if (player == null || level == null) {
            clearGeometry();
            return;
        }
        ItemStack tool = player.getMainHandItem();
        ToolShapeData data = ModTags.shapeOf(tool);
        if (data == null || !data.worldPreview() || data.config().isEmpty()
                || !(minecraft.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            clearGeometry();
            return;
        }
        ShapeConfig config = data.config().get();
        ShapeLimits limits = ClientState.limits();
        Target next = new Target(config, limits, hit.getBlockPos(), hit.getDirection(), player.getDirection());
        if (!reclassify && next.equals(target)) {
            return;
        }
        clearGeometry();
        target = next;
        if (!config.equals(cachedConfig) || !limits.equals(cachedLimits)) {
            cachedConfig = config;
            cachedLimits = limits;
            cachedResult = ShapeGenerator.generate(config, limits);
        }
        if (!(cachedResult instanceof ShapeGenerator.Valid valid)) {
            return;
        }
        BlockPos start = next.origin();
        BlockState startState = level.getBlockState(start);
        if (startState.hasBlockEntity()) {
            // The server never starts an area from storage or machines; show nothing extra.
            return;
        }
        Orientation orientation = Orientation.orient(next.face(), next.facing(),
                Orientation.Mode.forType(config.type()));
        float startProgress = startState.getDestroyProgress(player, level, start);
        Map<Offset, CellCategory> cells = new LinkedHashMap<>();
        for (Offset offset : valid.shape().worldOffsets(orientation)) {
            CellCategory category = offset.equals(Offset.ZERO)
                    ? CellCategory.START
                    : classify(level, player, tool, start.offset(offset.x(), offset.y(), offset.z()), startProgress);
            cells.put(offset, category);
            COUNTS.merge(category, 1, Integer::sum);
        }
        origin = start;
        contour = ContourEdges.of(PreviewFill.minedCells(cells));
        fill = PreviewFill.of(cells);
    }

    /** A client-side estimate of the server's checks; protection is unknown here and not guessed. */
    private static CellCategory classify(ClientLevel level, Player player, ItemStack tool, BlockPos pos,
            float startProgress) {
        if (level.isOutsideBuildHeight(pos) || !level.getWorldBorder().isWithinBounds(pos)) {
            return CellCategory.EXCLUDED;
        }
        if (!level.isLoaded(pos)) {
            return CellCategory.UNLOADED;
        }
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            return CellCategory.AIR;
        }
        if (state.hasBlockEntity() || state.getBlock() instanceof LiquidBlock || state.getDestroySpeed(level, pos) < 0) {
            return CellCategory.EXCLUDED;
        }
        if (!player.isCreative()) {
            if (state.requiresCorrectToolForDrops() && !tool.isCorrectToolForDrops(state)) {
                return CellCategory.EXCLUDED;
            }
            if (state.getDestroyProgress(player, level, pos) < startProgress * 0.9999F) {
                return CellCategory.EXCLUDED;
            }
        }
        return CellCategory.PRESENT;
    }

    static void onRenderLevel(RenderLevelStageEvent event) {
        // After particles: NeoForge's documented stage for translucent world overlays.
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        update(minecraft, false);
        // The single-block key hides the drawing only; the counts stay for the HUD details.
        if (origin == null || ClientState.bypassActive()) {
            return;
        }
        Vec3 camera = event.getCamera().getPosition();
        double baseX = origin.getX() - camera.x;
        double baseY = origin.getY() - camera.y;
        double baseZ = origin.getZ() - camera.z;

        // Draw like the vanilla block outline: the frame's view matrix on the global model-view,
        // the frame's projection, and camera-relative vertices. Both come from the event, so the
        // geometry stays on the blocks whatever renderer or camera mod produced them.
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.set(event.getModelViewMatrix());
        RenderSystem.applyModelViewMatrix();
        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(event.getProjectionMatrix(), RenderSystem.getVertexSorting());
        try {
            PoseStack.Pose pose = new PoseStack().last();
            MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();

            VertexConsumer quads = buffers.getBuffer(PreviewRenderTypes.FILL);
            for (PreviewFill.Face face : fill.faces()) {
                face(quads, pose, baseX + face.x(), baseY + face.y(), baseZ + face.z(), face.side(), FILL);
            }
            fill.start().ifPresent(start -> {
                for (PreviewFill.Side side : PreviewFill.Side.values()) {
                    face(quads, pose, baseX + start.x(), baseY + start.y(), baseZ + start.z(), side, START_FILL);
                }
            });
            buffers.endBatch(PreviewRenderTypes.FILL);

            VertexConsumer lines = buffers.getBuffer(RenderType.lines());
            for (ContourEdges.Edge edge : contour) {
                edge(lines, pose, baseX + edge.x(), baseY + edge.y(), baseZ + edge.z(), edge.axis());
            }
            fill.start().ifPresent(start -> {
                double x = baseX + start.x();
                double y = baseY + start.y();
                double z = baseZ + start.z();
                LevelRenderer.renderLineBox(new PoseStack(), lines, new AABB(x, y, z, x + 1, y + 1, z + 1).inflate(0.004),
                        START_LINE[0], START_LINE[1], START_LINE[2], START_LINE[3]);
            });
            buffers.endBatch(RenderType.lines());
        } finally {
            RenderSystem.restoreProjectionMatrix();
            modelView.popMatrix();
            RenderSystem.applyModelViewMatrix();
        }
    }

    private static void edge(VertexConsumer lines, PoseStack.Pose pose, double x, double y, double z,
            ContourEdges.Axis axis) {
        float dx = axis == ContourEdges.Axis.X ? 1.0F : 0.0F;
        float dy = axis == ContourEdges.Axis.Y ? 1.0F : 0.0F;
        float dz = axis == ContourEdges.Axis.Z ? 1.0F : 0.0F;
        lines.addVertex(pose, (float) x, (float) y, (float) z)
                .setColor(CONTOUR[0], CONTOUR[1], CONTOUR[2], CONTOUR[3])
                .setNormal(pose, dx, dy, dz);
        lines.addVertex(pose, (float) x + dx, (float) y + dy, (float) z + dz)
                .setColor(CONTOUR[0], CONTOUR[1], CONTOUR[2], CONTOUR[3])
                .setNormal(pose, dx, dy, dz);
    }

    /**
     * One face of the unit cube at (x, y, z), lifted slightly outwards along its normal only, so
     * neighbouring faces of a flat wall meet without overlapping.
     */
    private static void face(VertexConsumer quads, PoseStack.Pose pose, double x, double y, double z,
            PreviewFill.Side side, float[] color) {
        double x1 = x + 1;
        double y1 = y + 1;
        double z1 = z + 1;
        switch (side) {
            case WEST -> {
                double p = x - FACE_LIFT;
                quad(quads, pose, color, p, y, z, p, y, z1, p, y1, z1, p, y1, z);
            }
            case EAST -> {
                double p = x1 + FACE_LIFT;
                quad(quads, pose, color, p, y, z, p, y1, z, p, y1, z1, p, y, z1);
            }
            case DOWN -> {
                double p = y - FACE_LIFT;
                quad(quads, pose, color, x, p, z, x1, p, z, x1, p, z1, x, p, z1);
            }
            case UP -> {
                double p = y1 + FACE_LIFT;
                quad(quads, pose, color, x, p, z, x, p, z1, x1, p, z1, x1, p, z);
            }
            case NORTH -> {
                double p = z - FACE_LIFT;
                quad(quads, pose, color, x, y, p, x, y1, p, x1, y1, p, x1, y, p);
            }
            case SOUTH -> {
                double p = z1 + FACE_LIFT;
                quad(quads, pose, color, x, y, p, x1, y, p, x1, y1, p, x, y1, p);
            }
        }
    }

    private static void quad(VertexConsumer quads, PoseStack.Pose pose, float[] color, double... c) {
        for (int i = 0; i < 12; i += 3) {
            quads.addVertex(pose, (float) c[i], (float) c[i + 1], (float) c[i + 2])
                    .setColor(color[0], color[1], color[2], color[3]);
        }
    }
}
