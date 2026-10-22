package com.byflx.shapeexcavator.client;

import com.byflx.shapeexcavator.ShapeExcavator;
import com.byflx.shapeexcavator.net.BypassPayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * Client-only entry point: key mappings, the in-world preview and the tool hint. Only constructed
 * on the physical client, so a dedicated server never loads these classes.
 */
@Mod(value = ShapeExcavator.MOD_ID, dist = Dist.CLIENT)
public final class ShapeExcavatorClient {
    private static final String CATEGORY = "key.categories.shapeexcavator";
    private static KeyMapping openEditor;
    private static KeyMapping singleBlock;
    private static boolean sentBypass;

    public ShapeExcavatorClient(IEventBus modBus) {
        modBus.addListener(ShapeExcavatorClient::registerKeys);
        NeoForge.EVENT_BUS.addListener(ShapeExcavatorClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(ShapeExcavatorClient::onInteractionKey);
        NeoForge.EVENT_BUS.addListener(ShapeExcavatorClient::onLoggingOut);
        NeoForge.EVENT_BUS.addListener(WorldPreview::onRenderLevel);
        NeoForge.EVENT_BUS.addListener(ToolHud::onRenderGui);
    }

    private static void registerKeys(RegisterKeyMappingsEvent event) {
        // Created here rather than statically, so nothing touches input state before the client exists.
        openEditor = new KeyMapping("key.shapeexcavator.open_editor", KeyConflictContext.IN_GAME,
                InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, CATEGORY);
        singleBlock = new KeyMapping("key.shapeexcavator.single_block", KeyConflictContext.IN_GAME,
                InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_SHIFT, CATEGORY);
        event.register(openEditor);
        event.register(singleBlock);
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (openEditor != null) {
            while (openEditor.consumeClick()) {
                ClientAccess.openEditor();
            }
        }
        syncBypass(minecraft);
        WorldPreview.tick(minecraft);
    }

    /**
     * Fired inside the client tick right before an attack sends its mining action. Syncing here puts
     * a just-pressed or just-released single-block key on the wire ahead of the break, so the
     * server sees the key state the player had when mining.
     */
    private static void onInteractionKey(InputEvent.InteractionKeyMappingTriggered event) {
        if (event.isAttack()) {
            syncBypass(Minecraft.getInstance());
        }
    }

    private static void syncBypass(Minecraft minecraft) {
        boolean bypass = minecraft.player != null && minecraft.screen == null && isSingleBlockKeyDown(minecraft);
        ClientState.setBypassActive(bypass);
        if (bypass != sentBypass && minecraft.getConnection() != null) {
            sentBypass = bypass;
            PacketDistributor.sendToServer(new BypassPayload(bypass));
        }
    }

    /**
     * The single-block key shares Shift with sneaking by default. Polling the bound key directly
     * keeps it working even when another mapping is bound to the same key.
     */
    private static boolean isSingleBlockKeyDown(Minecraft minecraft) {
        if (singleBlock == null) {
            return false;
        }
        if (singleBlock.isDown()) {
            return true;
        }
        InputConstants.Key key = singleBlock.getKey();
        return key.getType() == InputConstants.Type.KEYSYM && key.getValue() != InputConstants.UNKNOWN.getValue()
                && InputConstants.isKeyDown(minecraft.getWindow().getWindow(), key.getValue());
    }

    private static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        sentBypass = false;
        ClientState.reset();
        WorldPreview.clear();
    }
}
