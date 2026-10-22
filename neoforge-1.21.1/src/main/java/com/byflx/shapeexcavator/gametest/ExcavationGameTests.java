package com.byflx.shapeexcavator.gametest;

import com.byflx.shapeexcavator.ShapeExcavator;
import com.byflx.shapeexcavator.data.ToolShapeData;
import com.byflx.shapeexcavator.excavation.ExcavationService;
import com.byflx.shapeexcavator.gametest.GameTestSupport.TestPlayer;
import com.byflx.shapeexcavator.registry.ModComponents;
import com.byflx.shapeexcavator.registry.ModItems;
import com.byflx.shapeexcavator.shape.ShapeConfig;
import com.byflx.shapeexcavator.shape.ShapeParams;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * World-level excavation checks (plan §12, "Abbau und Multiplayer"; run with
 * {@code ./gradlew runGameTestServer}). Each test builds a 5 × 5 × 2 wall, lets a survival player
 * looking north at its south face mine the centre with a configured tool through the real
 * {@code destroyBlock} path, and checks the world a few ticks later.
 *
 * <p>With a 3 × 3 × 1 cuboid the selection is the centre ±1 in x and y in the front layer
 * (plan §7: south face, player facing north: right = east, up = up, forward = north).
 */
@GameTestHolder(ShapeExcavator.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ExcavationGameTests {
    private static final BlockPos START = new BlockPos(4, 3, 3);
    /** Feet of the player: eye at the start block's height, 2.5 blocks south of its south face. */
    private static final BlockPos FEET = new BlockPos(4, 2, 6);
    private static final int SETTLE_TICKS = 5;
    private static final ShapeParams SQUARE = new ShapeParams.Cuboid(3, 3, 1);

    private ExcavationGameTests() {
    }

    @GameTest(template = GameTestSupport.EMPTY_TEMPLATE)
    public static void successfulStartMinesTheShapeExactlyOnce(GameTestHelper helper) {
        buildWall(helper, Blocks.DIRT);
        for (BlockPos pos : wall()) {
            GameTestSupport.trackDrops(helper.absolutePos(pos));
        }
        TestPlayer player = playerWithTool(helper, SQUARE);
        player.gameMode.destroyBlock(helper.absolutePos(START));

        helper.runAfterDelay(SETTLE_TICKS, () -> {
            for (BlockPos pos : wall()) {
                boolean selected = inSquare(pos);
                if (selected) {
                    helper.assertBlockNotPresent(Blocks.DIRT, pos);
                } else {
                    // Outside the shape or in the back layer: no recursive cascade from the extras.
                    helper.assertBlockPresent(Blocks.DIRT, pos);
                }
                int drops = GameTestSupport.dropCount(helper.absolutePos(pos));
                if (drops != (selected ? 1 : 0)) {
                    helper.fail("Drops at " + pos + " handled " + drops + " times");
                }
            }
            expectItems(helper, Items.DIRT, 9);
            expectDamage(helper, player, 9);
            expectMined(helper, player, 9);
            finish(helper, player);
        });
    }

    @GameTest(template = GameTestSupport.EMPTY_TEMPLATE)
    public static void protectedStartStartsNothing(GameTestHelper helper) {
        buildWall(helper, Blocks.DIRT);
        BlockPos start = helper.absolutePos(START);
        GameTestSupport.protect(start);
        TestPlayer player = playerWithTool(helper, SQUARE);
        player.gameMode.destroyBlock(start);

        helper.runAfterDelay(SETTLE_TICKS, () -> {
            GameTestSupport.unprotect(start);
            for (BlockPos pos : wall()) {
                helper.assertBlockPresent(Blocks.DIRT, pos);
            }
            expectItems(helper, Items.DIRT, 0);
            expectDamage(helper, player, 0);
            finish(helper, player);
        });
    }

    @GameTest(template = GameTestSupport.EMPTY_TEMPLATE)
    public static void protectedExtraBlockIsSkipped(GameTestHelper helper) {
        buildWall(helper, Blocks.DIRT);
        BlockPos guarded = START.east().above();
        GameTestSupport.protect(helper.absolutePos(guarded));
        TestPlayer player = playerWithTool(helper, SQUARE);
        player.gameMode.destroyBlock(helper.absolutePos(START));

        helper.runAfterDelay(SETTLE_TICKS, () -> {
            GameTestSupport.unprotect(helper.absolutePos(guarded));
            for (BlockPos pos : wall()) {
                if (inSquare(pos) && !pos.equals(guarded)) {
                    helper.assertBlockNotPresent(Blocks.DIRT, pos);
                } else {
                    helper.assertBlockPresent(Blocks.DIRT, pos);
                }
            }
            expectItems(helper, Items.DIRT, 8);
            expectDamage(helper, player, 8);
            expectMined(helper, player, 8);
            finish(helper, player);
        });
    }

    @GameTest(template = GameTestSupport.EMPTY_TEMPLATE)
    public static void machinesAndWrongTierAreSkipped(GameTestHelper helper) {
        buildWall(helper, Blocks.STONE);
        BlockPos machine = START.west();
        BlockPos obsidian = START.east();
        helper.setBlock(machine, Blocks.FURNACE);
        helper.setBlock(obsidian, Blocks.OBSIDIAN);
        TestPlayer player = playerWithTool(helper, SQUARE);
        player.gameMode.destroyBlock(helper.absolutePos(START));

        helper.runAfterDelay(SETTLE_TICKS, () -> {
            helper.assertBlockPresent(Blocks.FURNACE, machine);
            helper.assertBlockPresent(Blocks.OBSIDIAN, obsidian);
            for (BlockPos pos : wall()) {
                if (inSquare(pos) && !pos.equals(machine) && !pos.equals(obsidian)) {
                    helper.assertBlockNotPresent(Blocks.STONE, pos);
                }
            }
            expectItems(helper, Items.COBBLESTONE, 7);
            expectMined(helper, player, 7);
            finish(helper, player);
        });
    }

    @GameTest(template = GameTestSupport.EMPTY_TEMPLATE)
    public static void machineAsStartBlockStartsNothing(GameTestHelper helper) {
        buildWall(helper, Blocks.STONE);
        helper.setBlock(START, Blocks.FURNACE);
        TestPlayer player = playerWithTool(helper, SQUARE);
        player.gameMode.destroyBlock(helper.absolutePos(START));

        helper.runAfterDelay(SETTLE_TICKS, () -> {
            helper.assertBlockNotPresent(Blocks.FURNACE, START);
            for (BlockPos pos : wall()) {
                if (!pos.equals(START)) {
                    helper.assertBlockPresent(Blocks.STONE, pos);
                }
            }
            expectDamage(helper, player, 1);
            finish(helper, player);
        });
    }

    @GameTest(template = GameTestSupport.EMPTY_TEMPLATE)
    public static void singleBlockKeyMinesOnlyTheStart(GameTestHelper helper) {
        buildWall(helper, Blocks.DIRT);
        TestPlayer player = playerWithTool(helper, SQUARE);
        ExcavationService.setBypass(player, true);
        player.gameMode.destroyBlock(helper.absolutePos(START));

        helper.runAfterDelay(SETTLE_TICKS, () -> {
            helper.assertBlockNotPresent(Blocks.DIRT, START);
            for (BlockPos pos : wall()) {
                if (!pos.equals(START)) {
                    helper.assertBlockPresent(Blocks.DIRT, pos);
                }
            }
            expectItems(helper, Items.DIRT, 1);
            expectDamage(helper, player, 1);
            finish(helper, player);
        });
    }

    @GameTest(template = GameTestSupport.EMPTY_TEMPLATE)
    public static void unconfiguredToolMinesOneBlock(GameTestHelper helper) {
        buildWall(helper, Blocks.DIRT);
        TestPlayer player = GameTestSupport.spawnSurvivalPlayer(helper);
        place(helper, player);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SHAPE_EXCAVATOR.get()));
        player.gameMode.destroyBlock(helper.absolutePos(START));

        helper.runAfterDelay(SETTLE_TICKS, () -> {
            for (BlockPos pos : wall()) {
                if (!pos.equals(START)) {
                    helper.assertBlockPresent(Blocks.DIRT, pos);
                }
            }
            expectItems(helper, Items.DIRT, 1);
            finish(helper, player);
        });
    }

    /** The 5 × 5 × 2 wall around the start block: x 2..6, y 1..5, z 3..4. */
    private static List<BlockPos> wall() {
        return BlockPos.betweenClosedStream(new BlockPos(2, 1, 3), new BlockPos(6, 5, 4))
                .map(BlockPos::immutable)
                .toList();
    }

    private static boolean inSquare(BlockPos pos) {
        return pos.getZ() == START.getZ()
                && Math.abs(pos.getX() - START.getX()) <= 1
                && Math.abs(pos.getY() - START.getY()) <= 1;
    }

    private static void buildWall(GameTestHelper helper, Block block) {
        for (BlockPos pos : wall()) {
            helper.setBlock(pos, block);
        }
    }

    private static TestPlayer playerWithTool(GameTestHelper helper, ShapeParams params) {
        TestPlayer player = GameTestSupport.spawnSurvivalPlayer(helper);
        place(helper, player);
        ItemStack tool = new ItemStack(ModItems.SHAPE_EXCAVATOR.get());
        tool.set(ModComponents.TOOL_SHAPE.get(), ToolShapeData.fresh().withConfig(ShapeConfig.of(params), 1L, false));
        player.setItemInHand(InteractionHand.MAIN_HAND, tool);
        return player;
    }

    /** Facing north (yaw 180) towards the wall's south face. */
    private static void place(GameTestHelper helper, TestPlayer player) {
        BlockPos feet = helper.absolutePos(FEET);
        player.moveTo(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, 180.0F, 0.0F);
    }

    /** Total count of dropped items of that kind around the test area; merged stacks count fully. */
    private static void expectItems(GameTestHelper helper, Item item, int expected) {
        BlockPos min = helper.absolutePos(new BlockPos(-1, -3, -1));
        BlockPos max = helper.absolutePos(new BlockPos(10, 9, 10));
        AABB box = new AABB(Math.min(min.getX(), max.getX()), Math.min(min.getY(), max.getY()),
                Math.min(min.getZ(), max.getZ()), Math.max(min.getX(), max.getX()),
                Math.max(min.getY(), max.getY()), Math.max(min.getZ(), max.getZ()));
        int count = 0;
        for (ItemEntity entity : helper.getLevel().getEntitiesOfClass(ItemEntity.class, box)) {
            if (entity.getItem().is(item)) {
                count += entity.getItem().getCount();
            }
        }
        if (count != expected) {
            helper.fail("Expected " + expected + " dropped " + item + ", found " + count);
        }
    }

    /** One point of wear per mined block: the start block plus every mined extra block. */
    private static void expectDamage(GameTestHelper helper, TestPlayer player, int expected) {
        int damage = player.getMainHandItem().getDamageValue();
        if (damage != expected) {
            helper.fail("Expected tool damage " + expected + ", found " + damage);
        }
    }

    /** The result message's mined count includes the start block. */
    private static void expectMined(GameTestHelper helper, TestPlayer player, int expected) {
        Component message = player.lastMessage;
        if (message == null || !(message.getContents() instanceof TranslatableContents contents)
                || !contents.getKey().equals("message.shapeexcavator.result")) {
            helper.fail("Expected an excavation result message, got " + message);
            return;
        }
        Object mined = contents.getArgs()[0];
        if (!Integer.valueOf(expected).equals(mined)) {
            helper.fail("Expected " + expected + " mined in the result message, got " + mined);
        }
    }

    private static void finish(GameTestHelper helper, TestPlayer player) {
        for (BlockPos pos : wall()) {
            GameTestSupport.untrackDrops(helper.absolutePos(pos));
        }
        GameTestSupport.remove(player);
        helper.succeed();
    }
}
