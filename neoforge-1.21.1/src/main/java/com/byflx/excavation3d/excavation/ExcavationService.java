package com.byflx.excavation3d.excavation;

import com.byflx.excavation3d.config.ServerConfig;
import com.byflx.excavation3d.data.ToolShapeData;
import com.byflx.excavation3d.registry.ModComponents;
import com.byflx.excavation3d.registry.ModTags;
import com.byflx.excavation3d.shape.Offset;
import com.byflx.excavation3d.shape.Orientation;
import com.byflx.excavation3d.shape.ShapeConfig;
import com.byflx.excavation3d.shape.ShapeGenerator;
import com.byflx.excavation3d.text.ShapeTexts;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.bus.api.Event;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Server-side shape excavation (plan §10). All state lives on the server main thread.
 *
 * <ul>
 *   <li>The start block's {@link BlockEvent.BreakEvent} (lowest priority, only if nobody cancelled
 *       it so far) records an unconfirmed attempt with the orientation frozen at that moment; the
 *       hit face is checked on the server ({@link HitFaces}). A later listener may still cancel the
 *       break and the block may still refuse its removal, so the attempt is only confirmed by the
 *       {@link BlockDropsEvent} of this player's own break, which the player's survival
 *       {@code destroyBlock} fires only after the block was removed. The operation starts at the
 *       end of the tick only if the attempt was confirmed and the start block is still gone; see
 *       {@link StartGate}. Another player's break of the same block never starts it. Creative
 *       breaks have no such signal and therefore always mine a single block.</li>
 *   <li>The single-block key blocks the attempt, also when its update arrives after the break
 *       within the same tick.</li>
 *   <li>Extra blocks go through the player's own {@code ServerPlayerGameMode.destroyBlock}, which
 *       fires the break event for protection mods and handles loot, experience and tool wear once
 *       per block. A block only counts as mined if it is really gone afterwards
 *       ({@link ExtraBreak}). Breaks caused by the operation never start another operation.</li>
 *   <li>Positions come only from the stored, server-validated shape; the client never sends
 *       coordinates. Each position is re-checked right before it is mined.</li>
 * </ul>
 */
public final class ExcavationService {
    public enum SkipReason {
        AIR, OUTSIDE_WORLD, UNLOADED, BLOCK_ENTITY, LIQUID, UNBREAKABLE, PROTECTED, REFUSED, WRONG_TOOL,
        HARDER_THAN_START
    }

    public enum AbortReason {
        TOOL_CHANGED, TOOL_BROKEN, PLAYER_GONE, DIED, DIMENSION_CHANGED
    }

    /** Immutable server-side snapshot; mined includes the confirmed start block. */
    public record Result(int mined, Map<SkipReason, Integer> skippedReasons, AbortReason abortReason) {
        public Result {
            skippedReasons = Map.copyOf(skippedReasons);
        }

        /** Air is recorded in the reasons but does not count as a skipped block. */
        public int skippedTotal() {
            return skippedReasons.entrySet().stream()
                    .filter(entry -> entry.getKey() != SkipReason.AIR)
                    .mapToInt(Map.Entry::getValue)
                    .sum();
        }
    }

    /**
     * Posted once by {@link #finish} on the server thread for a finished operation, including
     * interruptions. Observers such as GameTests receive a snapshot without any client message
     * or packet. Not cancellable; the operation has already finished.
     */
    public static final class FinishedEvent extends Event {
        private final ServerPlayer player;
        private final Result result;

        public FinishedEvent(ServerPlayer player, Result result) {
            this.player = player;
            this.result = result;
        }

        public ServerPlayer getPlayer() {
            return player;
        }

        public Result getResult() {
            return result;
        }
    }

    private record ClickedFace(BlockPos pos, Direction face) {
    }

    private record Pending(ServerLevel level, BlockPos pos, BlockState state, List<Offset> offsets,
            float startProgress, int slot, UUID toolId) {
    }

    private static final class Operation {
        final ServerPlayer player;
        final ServerLevel level;
        final int slot;
        final UUID toolId;
        final float startProgress;
        final List<BlockPos> targets;
        final Map<SkipReason, Integer> skipped = new EnumMap<>(SkipReason.class);
        int next;
        /** Starts at 1: an operation only exists once the start block was confirmed removed. */
        int mined = 1;
        boolean finished;

        Operation(ServerPlayer player, Pending pending, List<BlockPos> targets) {
            this.player = player;
            this.level = pending.level();
            this.slot = pending.slot();
            this.toolId = pending.toolId();
            this.startProgress = pending.startProgress();
            this.targets = targets;
        }

        int remaining() {
            return targets.size() - next;
        }
    }

    private static final Map<UUID, ClickedFace> LAST_FACE = new HashMap<>();
    private static final StartGate<Pending> STARTS = new StartGate<>();
    private static final Map<UUID, Operation> OPERATIONS = new LinkedHashMap<>();
    private static final Set<UUID> BREAKING_EXTRA = new HashSet<>();
    private static int rotation;

    private ExcavationService() {
    }

    public static void setBypass(ServerPlayer player, boolean active) {
        STARTS.setBypass(player.getUUID(), active);
    }

    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (!event.getLevel().isClientSide() && event.getFace() != null) {
            LAST_FACE.put(event.getEntity().getUUID(), new ClickedFace(event.getPos().immutable(), event.getFace()));
        }
    }

    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player) || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        UUID id = player.getUUID();
        if (BREAKING_EXTRA.contains(id) || STARTS.bypassActive(id)) {
            return;
        }
        ItemStack tool = player.getMainHandItem();
        if (!tool.is(ModTags.SHAPE_TOOLS)) {
            return;
        }
        ToolShapeData data = tool.get(ModComponents.TOOL_SHAPE.get());
        if (data == null || !data.enabled()) {
            // No shape: the tool mines exactly like vanilla (plan §2a), without any message.
            return;
        }
        if (data.config().isEmpty()) {
            // Unreadable: safe single-block fallback; tooltip and hint say so.
            return;
        }
        BlockState state = event.getState();
        if (state.hasBlockEntity() || !ToolSuitability.isIntendedFor(tool, state)
                || (state.requiresCorrectToolForDrops() && !tool.isCorrectToolForDrops(state))) {
            // Machines, unrelated materials and insufficient tiers keep vanilla single-block mining.
            return;
        }
        if (player.isCreative()) {
            // Creative destroyBlock ignores whether the block refused its removal and fires nothing
            // afterwards, so a successful start cannot be proven (plan §10): single block only.
            player.displayClientMessage(Component.translatable("message.excavation3d.creative_single_block"), true);
            return;
        }
        if (OPERATIONS.containsKey(id)) {
            player.displayClientMessage(Component.translatable("message.excavation3d.busy"), true);
            return;
        }
        ShapeConfig config = data.config().get();
        ShapeGenerator.Result result = ShapeGenerator.generate(config, ServerConfig.limits());
        if (!(result instanceof ShapeGenerator.Valid valid)) {
            ShapeGenerator.Invalid invalid = (ShapeGenerator.Invalid) result;
            player.displayClientMessage(Component.translatable("message.excavation3d.invalid_single_block",
                    ShapeTexts.issue(invalid.issues().get(0))), true);
            return;
        }
        BlockPos pos = event.getPos().immutable();
        Orientation orientation = Orientation.orient(hitFace(player, level, pos, state), player.getDirection(),
                config.orientationMode());
        STARTS.attempt(id, pos, new Pending(level, pos, state, valid.shape().worldOffsets(orientation),
                state.getDestroyProgress(player, level, pos), player.getInventory().selected, data.toolId()));
    }

    /**
     * The player's survival {@code destroyBlock} calls {@code playerDestroy}, which handles the
     * drops, only if the block was really removed. So drops with this player as breaker at the
     * attempted position prove that this player's own break of the start block succeeded. Received
     * even if another mod cancelled the drops: the block is gone either way.
     */
    public static void onBlockDrops(BlockDropsEvent event) {
        if (event.getBreaker() instanceof ServerPlayer player && !BREAKING_EXTRA.contains(player.getUUID())) {
            STARTS.confirmRemoved(player.getUUID(), event.getPos());
        }
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        startPending(event.getServer());
        if (OPERATIONS.isEmpty()) {
            return;
        }
        List<Operation> operations = new ArrayList<>(OPERATIONS.values());
        int[] demands = new int[operations.size()];
        for (int i = 0; i < demands.length; i++) {
            demands[i] = operations.get(i).remaining();
        }
        int[] allocation = BudgetScheduler.allocate(demands, ServerConfig.playerBudget(),
                ServerConfig.globalBudget(), rotation++);
        for (int i = 0; i < allocation.length; i++) {
            process(operations.get(i), allocation[i]);
        }
        OPERATIONS.values().removeIf(operation -> operation.finished);
    }

    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        OPERATIONS.remove(id);
        STARTS.removePlayer(id);
        LAST_FACE.remove(id);
    }

    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        abort(event.getEntity().getUUID(), AbortReason.DIMENSION_CHANGED);
    }

    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            abort(player.getUUID(), AbortReason.DIED);
        }
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        OPERATIONS.clear();
        STARTS.clear();
        LAST_FACE.clear();
        BREAKING_EXTRA.clear();
    }

    private static void startPending(MinecraftServer server) {
        // Unconfirmed attempts (cancelled event, refused removal, creative) are dropped here even if
        // someone else removed the block in the meantime, as are starts whose block came back.
        Map<UUID, Pending> started = STARTS.drain(
                (pos, pending) -> blockRemoved(pending.level(), pending.pos(), pending.state()));
        for (Map.Entry<UUID, Pending> entry : started.entrySet()) {
            Pending pending = entry.getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null || OPERATIONS.containsKey(entry.getKey())) {
                continue;
            }
            List<BlockPos> targets = new ArrayList<>(pending.offsets().size());
            for (Offset offset : pending.offsets()) {
                if (!offset.equals(Offset.ZERO)) {
                    targets.add(pending.pos().offset(offset.x(), offset.y(), offset.z()));
                }
            }
            if (!targets.isEmpty()) {
                OPERATIONS.put(entry.getKey(), new Operation(player, pending, targets));
            }
        }
    }

    /**
     * The block is gone: the position holds what the removal leaves behind (air, or the block's
     * own fluid when waterlogged), or a fluid such as the water ice turns into. A changed property
     * or a different solid block does not count.
     */
    private static boolean blockRemoved(ServerLevel level, BlockPos pos, BlockState before) {
        BlockState now = level.getBlockState(pos);
        return now == before.getFluidState().createLegacyBlock() || now.isAir()
                || now.getBlock() instanceof LiquidBlock;
    }

    private static void process(Operation operation, int budget) {
        for (int i = 0; i < budget && operation.remaining() > 0; i++) {
            AbortReason abort = validate(operation);
            if (abort != null) {
                finish(operation, abort);
                return;
            }
            BlockPos pos = operation.targets.get(operation.next++);
            SkipReason skip = check(operation, pos);
            if (skip != null) {
                operation.skipped.merge(skip, 1, Integer::sum);
                continue;
            }
            UUID id = operation.player.getUUID();
            BlockState before = operation.level.getBlockState(pos);
            boolean destroyed;
            BREAKING_EXTRA.add(id);
            try {
                destroyed = operation.player.gameMode.destroyBlock(pos);
            } finally {
                BREAKING_EXTRA.remove(id);
            }
            // destroyBlock also returns true when the block refused its removal: check the world.
            switch (ExtraBreak.of(destroyed, blockRemoved(operation.level, pos, before))) {
                case MINED -> operation.mined++;
                case PROTECTED -> operation.skipped.merge(SkipReason.PROTECTED, 1, Integer::sum);
                case REFUSED -> operation.skipped.merge(SkipReason.REFUSED, 1, Integer::sum);
            }
        }
        if (operation.remaining() == 0) {
            finish(operation, null);
        }
    }

    private static AbortReason validate(Operation operation) {
        ServerPlayer player = operation.player;
        if (player.isRemoved() || player.hasDisconnected()) {
            return AbortReason.PLAYER_GONE;
        }
        if (!player.isAlive()) {
            return AbortReason.DIED;
        }
        if (player.serverLevel() != operation.level) {
            return AbortReason.DIMENSION_CHANGED;
        }
        ItemStack tool = player.getMainHandItem();
        if (tool.isEmpty() && player.getInventory().selected == operation.slot) {
            return AbortReason.TOOL_BROKEN;
        }
        ToolShapeData data = tool.get(ModComponents.TOOL_SHAPE.get());
        if (player.getInventory().selected != operation.slot || !tool.is(ModTags.SHAPE_TOOLS)
                || data == null || !data.enabled() || !data.toolId().equals(operation.toolId)) {
            return AbortReason.TOOL_CHANGED;
        }
        return null;
    }

    private static SkipReason check(Operation operation, BlockPos pos) {
        ServerLevel level = operation.level;
        ServerPlayer player = operation.player;
        if (level.isOutsideBuildHeight(pos) || !level.getWorldBorder().isWithinBounds(pos)) {
            return SkipReason.OUTSIDE_WORLD;
        }
        if (!level.isLoaded(pos)) {
            // Never load chunks for an excavation.
            return SkipReason.UNLOADED;
        }
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            return SkipReason.AIR;
        }
        if (state.hasBlockEntity()) {
            return SkipReason.BLOCK_ENTITY;
        }
        if (state.getBlock() instanceof LiquidBlock) {
            return SkipReason.LIQUID;
        }
        if (state.getDestroySpeed(level, pos) < 0) {
            return SkipReason.UNBREAKABLE;
        }
        if (!level.mayInteract(player, pos)
                || player.blockActionRestricted(level, pos, player.gameMode.getGameModeForPlayer())) {
            return SkipReason.PROTECTED;
        }
        ItemStack tool = player.getMainHandItem();
        if (!ToolSuitability.isIntendedFor(tool, state)
                || (state.requiresCorrectToolForDrops() && !tool.isCorrectToolForDrops(state))) {
            return SkipReason.WRONG_TOOL;
        }
        if (!player.isCreative()) {
            // V1 rule: nothing that takes longer to mine than the start block (no obsidian via dirt).
            if (state.getDestroyProgress(player, level, pos) < operation.startProgress * 0.9999F) {
                return SkipReason.HARDER_THAN_START;
            }
        }
        return null;
    }

    private static void abort(UUID id, AbortReason reason) {
        STARTS.forget(id);
        Operation operation = OPERATIONS.remove(id);
        if (operation != null) {
            finish(operation, reason);
        }
    }

    private static void finish(Operation operation, AbortReason abort) {
        if (operation.finished) {
            return;
        }
        operation.finished = true;
        Result result = new Result(operation.mined, operation.skipped, abort);
        NeoForge.EVENT_BUS.post(new FinishedEvent(operation.player, result));
        // Normal completion is quiet, including skipped blocks. Only interruptions need feedback.
        if (abort == null) {
            return;
        }
        MutableComponent message = Component.translatable("message.excavation3d.aborted",
                        Component.translatable("abort.excavation3d." + abort.name().toLowerCase(Locale.ROOT)),
                        result.mined(), result.skippedTotal());
        List<Component> reasons = new ArrayList<>();
        for (Map.Entry<SkipReason, Integer> entry : operation.skipped.entrySet()) {
            if (entry.getKey() != SkipReason.AIR) {
                reasons.add(Component.translatable(
                        "skip.excavation3d." + entry.getKey().name().toLowerCase(Locale.ROOT), entry.getValue()));
            }
        }
        if (!reasons.isEmpty()) {
            message.append(" (");
            for (int i = 0; i < reasons.size(); i++) {
                if (i > 0) {
                    message.append(", ");
                }
                message.append(reasons.get(i));
            }
            message.append(")");
        }
        if (!operation.player.hasDisconnected()) {
            operation.player.displayClientMessage(message, true);
        }
    }

    /** The face from the client's packet is only a candidate; {@link HitFaces} checks it. */
    private static Direction hitFace(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state) {
        ClickedFace clicked = LAST_FACE.get(player.getUUID());
        Direction claimed = clicked != null && clicked.pos().equals(pos) ? clicked.face() : null;
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getViewVector(1.0F).scale(player.blockInteractionRange() + 1.0));
        BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE,
                player));
        Direction traced = hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(pos)
                ? hit.getDirection() : null;
        VoxelShape shape = state.getShape(level, pos);
        AABB box = (shape.isEmpty() ? new AABB(0, 0, 0, 1, 1, 1) : shape.bounds())
                .move(pos.getX(), pos.getY(), pos.getZ());
        return HitFaces.choose(claimed, traced, box, eye);
    }
}
