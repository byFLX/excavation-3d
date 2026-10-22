package com.byflx.shapeexcavator.client;

import com.byflx.shapeexcavator.client.screen.ShapeEditorScreen;
import com.byflx.shapeexcavator.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.HitResult;

/** Client entry points called from common code; only ever invoked on the client. */
public final class ClientAccess {
    private ClientAccess() {
    }

    /** Right-click with the tool: opens the editor only when aiming into the air (plan §2). */
    public static boolean openEditorFromAirClick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.hitResult != null && minecraft.hitResult.getType() != HitResult.Type.MISS) {
            return false;
        }
        return openEditor();
    }

    /** Opens the editor for the tool in the main hand, if any. */
    public static boolean openEditor() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen != null
                || !minecraft.player.getMainHandItem().is(ModItems.SHAPE_EXCAVATOR.get())) {
            return false;
        }
        minecraft.setScreen(new ShapeEditorScreen(minecraft.player));
        return true;
    }
}
