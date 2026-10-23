package com.byflx.excavation3d.gametest;

import com.byflx.excavation3d.Excavation3D;
import com.byflx.excavation3d.data.ToolShapeData;
import com.byflx.excavation3d.excavation.ExcavationService;
import com.byflx.excavation3d.excavation.ExcavationService.Result;
import com.byflx.excavation3d.excavation.ExcavationService.SkipReason;
import com.byflx.excavation3d.gametest.GameTestSupport.TestPlayer;
import com.byflx.excavation3d.registry.ModComponents;
import com.byflx.excavation3d.registry.ModTags;
import com.byflx.excavation3d.shape.ShapeConfig;
import com.byflx.excavation3d.shape.ShapeParams;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * World-level excavation checks (plan §2a, §12 "Abbau und Multiplayer"; run with
 * {@code ./gradlew runGameTestServer}). Each mining test builds a 5 × 5 × 2 wall, lets a survival
 * player looking north at its south face mine the centre with a vanilla tool carrying a shape
 * through the real {@code destroyBlock} path, and checks the world a few ticks later. The
 * persistence tests run the vanilla anvil, enchanting table and smithing table menus directly.
 *
 * <p>With a 3 × 3 × 1 cuboid the selection is the centre ±1 in x and y in the front layer
 * (plan §7: south face, player facing north: right = east, up = up, forward = north).
 */
@GameTestHolder(Excavation3D.MOD_ID)
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
        TestPlayer player = playerWithTool(helper, Items.IRON_SHOVEL, SQUARE);
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
            expectResult(helper, player, 9, Map.of());
            finish(helper, player);
        });
    }

    @GameTest(template = GameTestSupport.EMPTY_TEMPLATE)
    public static void protectedStartStartsNothing(GameTestHelper helper) {
        buildWall(helper, Blocks.DIRT);
        BlockPos start = helper.absolutePos(START);
        GameTestSupport.protect(start);
        TestPlayer player = playerWithTool(helper, Items.IRON_SHOVEL, SQUARE);
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
        TestPlayer player = playerWithTool(helper, Items.IRON_SHOVEL, SQUARE);
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
            expectResult(helper, player, 8, Map.of(SkipReason.PROTECTED, 1));
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
        TestPlayer player = playerWithTool(helper, Items.IRON_PICKAXE, SQUARE);
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
            expectResult(helper, player, 7, Map.of(SkipReason.BLOCK_ENTITY, 1, SkipReason.WRONG_TOOL, 1));
            finish(helper, player);
        });
    }

    @GameTest(template = GameTestSupport.EMPTY_TEMPLATE)
    public static void machineAsStartBlockStartsNothing(GameTestHelper helper) {
        buildWall(helper, Blocks.STONE);
        helper.setBlock(START, Blocks.FURNACE);
        TestPlayer player = playerWithTool(helper, Items.IRON_PICKAXE, SQUARE);
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
        TestPlayer player = playerWithTool(helper, Items.IRON_SHOVEL, SQUARE);
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

    /** A tool without a shape stays vanilla (plan §2a): one block, one point of wear, no message. */
    @GameTest(template = GameTestSupport.EMPTY_TEMPLATE)
    public static void unconfiguredToolMinesOneBlock(GameTestHelper helper) {
        buildWall(helper, Blocks.DIRT);
        TestPlayer player = GameTestSupport.spawnSurvivalPlayer(helper);
        place(helper, player);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
        player.gameMode.destroyBlock(helper.absolutePos(START));

        helper.runAfterDelay(SETTLE_TICKS, () -> {
            for (BlockPos pos : wall()) {
                if (!pos.equals(START)) {
                    helper.assertBlockPresent(Blocks.DIRT, pos);
                }
            }
            expectItems(helper, Items.DIRT, 1);
            expectDamage(helper, player, 1);
            if (player.getMainHandItem().has(ModComponents.TOOL_SHAPE.get())) {
                helper.fail("Mining must not add a shape component to a vanilla tool");
            }
            if (player.lastMessage != null) {
                helper.fail("Expected no message for a tool without a shape, got " + player.lastMessage);
            }
            finish(helper, player);
        });
    }

    @GameTest(template = GameTestSupport.EMPTY_TEMPLATE)
    public static void pickaxeShapeMinesStone(GameTestHelper helper) {
        buildWall(helper, Blocks.STONE);
        TestPlayer player = playerWithTool(helper, Items.IRON_PICKAXE, SQUARE);
        player.gameMode.destroyBlock(helper.absolutePos(START));

        helper.runAfterDelay(SETTLE_TICKS, () -> {
            for (BlockPos pos : wall()) {
                if (inSquare(pos)) {
                    helper.assertBlockNotPresent(Blocks.STONE, pos);
                } else {
                    helper.assertBlockPresent(Blocks.STONE, pos);
                }
            }
            expectItems(helper, Items.COBBLESTONE, 9);
            expectDamage(helper, player, 9);
            expectResult(helper, player, 9, Map.of());
            finish(helper, player);
        });
    }

    /** Extra blocks the real tool is not correct for stay and appear in the server result. */
    @GameTest(template = GameTestSupport.EMPTY_TEMPLATE)
    public static void shovelShapeSkipsStoneAndReportsIt(GameTestHelper helper) {
        buildWall(helper, Blocks.DIRT);
        BlockPos stoneA = START.west();
        BlockPos stoneB = START.above();
        helper.setBlock(stoneA, Blocks.STONE);
        helper.setBlock(stoneB, Blocks.STONE);
        TestPlayer player = playerWithTool(helper, Items.IRON_SHOVEL, SQUARE);
        player.gameMode.destroyBlock(helper.absolutePos(START));

        helper.runAfterDelay(SETTLE_TICKS, () -> {
            helper.assertBlockPresent(Blocks.STONE, stoneA);
            helper.assertBlockPresent(Blocks.STONE, stoneB);
            for (BlockPos pos : wall()) {
                if (inSquare(pos) && !pos.equals(stoneA) && !pos.equals(stoneB)) {
                    helper.assertBlockNotPresent(Blocks.DIRT, pos);
                }
            }
            expectItems(helper, Items.DIRT, 7);
            expectItems(helper, Items.COBBLESTONE, 0);
            expectDamage(helper, player, 7);
            expectResult(helper, player, 7, Map.of(SkipReason.WRONG_TOOL, 2));
            finish(helper, player);
        });
    }

    @GameTest(template = GameTestSupport.EMPTY_TEMPLATE)
    public static void axeShapeMinesPlanks(GameTestHelper helper) {
        buildWall(helper, Blocks.OAK_PLANKS);
        TestPlayer player = playerWithTool(helper, Items.IRON_AXE, SQUARE);
        player.gameMode.destroyBlock(helper.absolutePos(START));

        helper.runAfterDelay(SETTLE_TICKS, () -> {
            for (BlockPos pos : wall()) {
                if (inSquare(pos)) {
                    helper.assertBlockNotPresent(Blocks.OAK_PLANKS, pos);
                } else {
                    helper.assertBlockPresent(Blocks.OAK_PLANKS, pos);
                }
            }
            expectItems(helper, Items.OAK_PLANKS, 9);
            expectDamage(helper, player, 9);
            expectResult(helper, player, 9, Map.of());
            finish(helper, player);
        });
    }

    /** Anvil repair with a material plus rename keeps the shape (plan §2a). */
    @GameTest(template = GameTestSupport.EMPTY_TEMPLATE)
    public static void shapeSurvivesAnvilRepairAndRename(GameTestHelper helper) {
        TestPlayer player = GameTestSupport.spawnSurvivalPlayer(helper);
        ItemStack tool = shapedTool(helper, Items.IRON_PICKAXE, SQUARE);
        tool.setDamageValue(100);
        ToolShapeData expected = tool.get(ModComponents.TOOL_SHAPE.get());
        AnvilMenu menu = new AnvilMenu(0, player.getInventory());
        menu.getSlot(0).set(tool);
        menu.getSlot(1).set(new ItemStack(Items.IRON_INGOT));
        menu.setItemName("Renamed");

        ItemStack result = menu.getSlot(2).getItem();
        if (!result.is(Items.IRON_PICKAXE) || result.getDamageValue() >= 100
                || !result.has(DataComponents.CUSTOM_NAME)) {
            helper.fail("Expected a repaired and renamed iron pickaxe, got " + result);
        }
        expectShape(helper, result, expected);
        finish(helper, player);
    }

    /** Enchanting at a table keeps the shape (plan §2a). */
    @GameTest(template = GameTestSupport.EMPTY_TEMPLATE)
    public static void shapeSurvivesEnchanting(GameTestHelper helper) {
        TestPlayer player = GameTestSupport.spawnSurvivalPlayer(helper);
        player.giveExperienceLevels(30);
        ItemStack tool = shapedTool(helper, Items.IRON_PICKAXE, SQUARE);
        ToolShapeData expected = tool.get(ModComponents.TOOL_SHAPE.get());
        EnchantmentMenu menu = new EnchantmentMenu(0, player.getInventory(),
                ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(START)));
        menu.getSlot(0).set(tool);
        menu.getSlot(1).set(new ItemStack(Items.LAPIS_LAZULI, 3));
        if (!menu.clickMenuButton(player, 0)) {
            helper.fail("The enchanting table offered nothing for the first slot (cost " + menu.costs[0] + ")");
        }

        ItemStack result = menu.getSlot(0).getItem();
        if (!result.is(Items.IRON_PICKAXE) || !result.isEnchanted()) {
            helper.fail("Expected an enchanted iron pickaxe, got " + result);
        }
        expectShape(helper, result, expected);
        finish(helper, player);
    }

    /** The netherite upgrade keeps the shape (plan §2a). */
    @GameTest(template = GameTestSupport.EMPTY_TEMPLATE)
    public static void shapeSurvivesNetheriteUpgrade(GameTestHelper helper) {
        TestPlayer player = GameTestSupport.spawnSurvivalPlayer(helper);
        ItemStack tool = shapedTool(helper, Items.DIAMOND_PICKAXE, SQUARE);
        ToolShapeData expected = tool.get(ModComponents.TOOL_SHAPE.get());
        SmithingMenu menu = new SmithingMenu(0, player.getInventory(),
                ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(START)));
        menu.getSlot(0).set(new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE));
        menu.getSlot(1).set(tool);
        menu.getSlot(2).set(new ItemStack(Items.NETHERITE_INGOT));

        ItemStack result = menu.getSlot(3).getItem();
        if (!result.is(Items.NETHERITE_PICKAXE)) {
            helper.fail("Expected a netherite pickaxe, got " + result);
        }
        expectShape(helper, result, expected);
        finish(helper, player);
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

    /** A vanilla tool from {@code excavation3d:shape_tools} carrying an accepted shape. */
    private static ItemStack shapedTool(GameTestHelper helper, Item item, ShapeParams params) {
        ItemStack tool = new ItemStack(item);
        if (!tool.is(ModTags.SHAPE_TOOLS)) {
            helper.fail(item + " is not in excavation3d:shape_tools");
        }
        tool.set(ModComponents.TOOL_SHAPE.get(), ToolShapeData.fresh().withConfig(ShapeConfig.of(params), 1L, false));
        return tool;
    }

    private static TestPlayer playerWithTool(GameTestHelper helper, Item item, ShapeParams params) {
        TestPlayer player = GameTestSupport.spawnSurvivalPlayer(helper);
        place(helper, player);
        player.setItemInHand(InteractionHand.MAIN_HAND, shapedTool(helper, item, params));
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

    /** Completion is observable once on the server, while the player receives no message. */
    private static void expectResult(GameTestHelper helper, TestPlayer player, int mined,
            Map<SkipReason, Integer> skippedReasons) {
        Result result = player.lastResult;
        if (result == null || player.resultCount != 1) {
            helper.fail("Expected exactly one excavation result, got " + player.resultCount + ": " + result);
            return;
        }
        if (result.abortReason() != null) {
            helper.fail("Expected normal completion, got " + result.abortReason());
        }
        if (result.mined() != mined) {
            helper.fail("Expected " + mined + " mined (including the start block), got " + result.mined());
        }
        int skipped = skippedReasons.values().stream().mapToInt(Integer::intValue).sum();
        if (result.skippedTotal() != skipped || !result.skippedReasons().equals(skippedReasons)) {
            helper.fail("Expected " + skipped + " skipped with reasons " + skippedReasons + ", got " + result);
        }
        if (player.lastMessage != null) {
            helper.fail("Expected silent excavation completion, got " + player.lastMessage);
        }
    }

    private static void expectShape(GameTestHelper helper, ItemStack stack, ToolShapeData expected) {
        ToolShapeData actual = stack.get(ModComponents.TOOL_SHAPE.get());
        if (expected == null || !Objects.equals(expected, actual)) {
            helper.fail("Expected the shape " + expected + " to survive, found " + actual);
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
