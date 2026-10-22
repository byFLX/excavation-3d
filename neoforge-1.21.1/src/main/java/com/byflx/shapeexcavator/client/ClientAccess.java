package com.byflx.shapeexcavator.client;

import com.byflx.shapeexcavator.client.screen.ShapeEditorScreen;
import com.byflx.shapeexcavator.registry.ModTags;
import net.minecraft.client.Minecraft;

/** Client-only editor entry. Right-clicks are never intercepted (plan §2a); only the editor key opens it. */
public final class ClientAccess {
    private ClientAccess() {
    }

    /** Opens the editor for the eligible tool in the main hand, if any. */
    public static boolean openEditor() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen != null
                || QuickShapeAction.busy()
                || !minecraft.player.getMainHandItem().is(ModTags.SHAPE_TOOLS)) {
            return false;
        }
        minecraft.setScreen(new ShapeEditorScreen(minecraft.player));
        return true;
    }
}
