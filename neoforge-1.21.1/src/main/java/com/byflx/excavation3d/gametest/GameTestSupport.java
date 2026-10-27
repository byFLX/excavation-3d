package com.byflx.excavation3d.gametest;

import com.byflx.excavation3d.Excavation3D;
import com.byflx.excavation3d.excavation.ExcavationService;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/**
 * Infrastructure for {@link ExcavationGameTests} (plan §12: Game Tests for world behaviour). Only
 * active on the Game Test server ({@code ./gradlew runGameTestServer}); on any other server the
 * started-listener returns immediately and nothing else is registered.
 *
 * <ul>
 *   <li>The tests build their scene themselves, so they use one empty template
 *       ({@link #EMPTY_TEMPLATE}) that is created in memory instead of a binary structure file.</li>
 *   <li>A stand-in for a protection mod cancels break events at positions a test protects.</li>
 *   <li>A drop counter records how often a position's drops (loot and experience) are handled.</li>
 *   <li>A server-side completion listener records excavation results for each test player.</li>
 * </ul>
 */
@EventBusSubscriber(modid = Excavation3D.MOD_ID)
public final class GameTestSupport {
    static final String EMPTY_TEMPLATE = "empty";
    private static final Vec3i TEMPLATE_SIZE = new Vec3i(9, 8, 9);

    private static final Set<BlockPos> PROTECTED = new HashSet<>();
    private static final Map<BlockPos, Integer> DROP_COUNTS = new HashMap<>();
    private static boolean listening;

    private GameTestSupport() {
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        if (!(event.getServer() instanceof GameTestServer server)) {
            return;
        }
        // Captures an untouched all-air box high above the test area: placing the template clears
        // the test area to air. Runs before the Game Test server starts its tests on the first tick.
        ServerLevel level = server.overworld();
        BlockPos source = new BlockPos(0, level.getMaxBuildHeight() - TEMPLATE_SIZE.getY(), 0);
        StructureTemplate template = level.getStructureManager()
                .getOrCreate(ResourceLocation.fromNamespaceAndPath(Excavation3D.MOD_ID, EMPTY_TEMPLATE));
        template.fillFromWorld(level, source, TEMPLATE_SIZE, false, null);
        if (!listening) {
            listening = true;
            NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, GameTestSupport::onBreak);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, GameTestSupport::onDrops);
            NeoForge.EVENT_BUS.addListener(GameTestSupport::onExcavationFinished);
        }
    }

    private static void onBreak(BlockEvent.BreakEvent event) {
        if (PROTECTED.contains(event.getPos())) {
            event.setCanceled(true);
        }
    }

    private static void onDrops(BlockDropsEvent event) {
        DROP_COUNTS.computeIfPresent(event.getPos(), (pos, count) -> count + 1);
    }

    private static void onExcavationFinished(ExcavationService.FinishedEvent event) {
        if (event.getPlayer() instanceof TestPlayer player) {
            // Snapshot delivery before entities have a physics/pickup tick.
            var box = new net.minecraft.world.phys.AABB(player.blockPosition()).inflate(5);
            player.deliveredItems = player.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, box)
                    .stream().map(item -> new DeliveredItem(item.getItem().copy(), item.position(), item.getDeltaMovement())).toList();
            player.deliveredXp = player.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.ExperienceOrb.class, box)
                    .stream().map(orb -> new DeliveredXp(orb.value, orb.position())).toList();
            player.lastResult = event.getResult();
            player.resultCount++;
        }
    }

    /** Cancels every break at the absolute position, like a claim mod would. */
    static void protect(BlockPos absolute) {
        PROTECTED.add(absolute.immutable());
    }

    static void unprotect(BlockPos absolute) {
        PROTECTED.remove(absolute);
    }

    /** Starts counting drop handling at the absolute position. */
    static void trackDrops(BlockPos absolute) {
        DROP_COUNTS.put(absolute.immutable(), 0);
    }

    static int dropCount(BlockPos absolute) {
        return DROP_COUNTS.getOrDefault(absolute, 0);
    }

    static void untrackDrops(BlockPos absolute) {
        DROP_COUNTS.remove(absolute);
    }

    /**
     * A real survival {@link ServerPlayer} in the player list, like vanilla's mock server player
     * but not forced into creative (the tool mines single blocks in creative).
     */
    static TestPlayer spawnSurvivalPlayer(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        GameProfile profile = new GameProfile(UUID.randomUUID(), "se-gametest");
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(profile, false);
        TestPlayer player = new TestPlayer(server, helper.getLevel(), profile, cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        server.getPlayerList().placeNewPlayer(connection, player, cookie);
        player.setGameMode(GameType.SURVIVAL);
        return player;
    }

    static void remove(TestPlayer player) {
        player.server.getPlayerList().remove(player);
    }

    record DeliveredItem(net.minecraft.world.item.ItemStack stack, net.minecraft.world.phys.Vec3 position,
            net.minecraft.world.phys.Vec3 motion) { }
    record DeliveredXp(int value, net.minecraft.world.phys.Vec3 position) { }

    /** Keeps server-side results separately from messages so tests can assert silent completion. */
    static final class TestPlayer extends ServerPlayer {
        java.util.List<DeliveredItem> deliveredItems = java.util.List.of();
        java.util.List<DeliveredXp> deliveredXp = java.util.List.of();
        ExcavationService.Result lastResult;
        int resultCount;
        Component lastMessage;

        TestPlayer(MinecraftServer server, ServerLevel level, GameProfile profile, ClientInformation info) {
            super(server, level, profile, info);
        }

        @Override
        public void displayClientMessage(Component message, boolean actionBar) {
            lastMessage = message;
        }
    }
}
