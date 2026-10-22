package com.byflx.shapeexcavator.item;

import com.byflx.shapeexcavator.client.ClientAccess;
import com.byflx.shapeexcavator.registry.ModComponents;
import com.byflx.shapeexcavator.registry.ModItems;
import com.byflx.shapeexcavator.shape.ShapeLimits;
import com.byflx.shapeexcavator.text.ShapeTexts;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * The hand tool (plan §2). Mines like an iron pickaxe and shovel. Right-clicking into the air
 * opens the editor on the client; right-clicking a block is left to the block (chests, machines),
 * the editor key works there instead. Shape excavation itself is server-side in
 * {@link com.byflx.shapeexcavator.excavation.ExcavationService}.
 */
public class ShapeExcavatorItem extends DiggerItem {
    public ShapeExcavatorItem(Item.Properties properties) {
        super(Tiers.IRON, ModItems.EXCAVATOR_MINEABLE,
                properties.attributes(DiggerItem.createAttributes(Tiers.IRON, 1.0F, -2.8F)));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        // ClientAccess is only loaded when this branch runs, which never happens on a dedicated server.
        if (hand == InteractionHand.MAIN_HAND && level.isClientSide() && ClientAccess.openEditorFromAirClick()) {
            return InteractionResultHolder.success(stack);
        }
        return InteractionResultHolder.pass(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip,
            TooltipFlag flag) {
        tooltip.add(ShapeTexts.toolSummary(stack.get(ModComponents.TOOL_SHAPE.get()), ShapeLimits.DEFAULT));
        tooltip.add(Component.translatable("tool.shapeexcavator.hint").withStyle(ChatFormatting.DARK_GRAY));
    }
}
