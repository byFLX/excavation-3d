package com.byflx.shapeexcavator.client;

import com.byflx.shapeexcavator.ShapeExcavator;
import com.byflx.shapeexcavator.data.ToolShapeData;
import com.byflx.shapeexcavator.net.BypassPayload;
import com.byflx.shapeexcavator.editor.QuickMenuLogic;
import com.byflx.shapeexcavator.client.screen.QuickShapeScreen;
import com.byflx.shapeexcavator.registry.ModTags;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * Client-only entry point: key mappings, the in-world preview, the tool hint and tooltip. Only constructed
 * on the physical client, so a dedicated server never loads these classes.
 */
@Mod(value = ShapeExcavator.MOD_ID, dist = Dist.CLIENT)
public final class ShapeExcavatorClient {
    private static final String CATEGORY = "key.categories.shapeexcavator";
    private static KeyMapping openEditor;
    private static KeyMapping singleBlock;
    private static boolean sentBypass;
    private static final QuickMenuLogic.Hold editorHold = new QuickMenuLogic.Hold();
    private static boolean suppressEditor;

    public ShapeExcavatorClient(IEventBus modBus) {
        modBus.addListener(ShapeExcavatorClient::registerKeys);
        NeoForge.EVENT_BUS.addListener(ShapeExcavatorClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(ShapeExcavatorClient::onInteractionKey);
        NeoForge.EVENT_BUS.addListener(ShapeExcavatorClient::onLoggingOut);
        NeoForge.EVENT_BUS.addListener(WorldPreview::onRenderLevel);
        NeoForge.EVENT_BUS.addListener(ToolHud::onRenderGui);
        NeoForge.EVENT_BUS.addListener(ShapeExcavatorClient::onTooltip);
    }

    /**
     * Eligible tools show the current shape or Off, and the editor, quick-menu and single-block keys.
     */
    private static void onTooltip(ItemTooltipEvent event) {
        ToolShapeData data = ModTags.shapeOf(event.getItemStack());
        if (!event.getItemStack().is(ModTags.SHAPE_TOOLS) || openEditor == null || singleBlock == null) {
            return;
        }
        List<Component> tooltip = event.getToolTip();
        int at = Math.min(1, tooltip.size());
        tooltip.add(at, ClientState.toolSummary(data));
        tooltip.add(at + 1, Component.translatable("tool.shapeexcavator.hint", openEditor.getTranslatedKeyMessage(),
                singleBlock.getTranslatedKeyMessage()).withStyle(ChatFormatting.DARK_GRAY));
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
            boolean clicked = false;
            while (openEditor.consumeClick()) clicked = true;
            boolean down = isEditorKeyDown();
            if (minecraft.screen instanceof QuickShapeScreen) {
                // Its tick selects on release. Leave the gesture consumed until then.
            } else if (minecraft.screen != null || minecraft.player == null || QuickShapeAction.busy()
                    || !minecraft.player.getMainHandItem().is(ModTags.SHAPE_TOOLS)) {
                editorHold.cancel();
                suppressEditor = down;
            } else if (suppressEditor) {
                if (!down) suppressEditor = false;
            } else {
                long now = net.minecraft.Util.getMillis();
                // A short tap may be pressed and released between two client ticks.
                if (clicked && !down) editorHold.update(true, now);
                QuickMenuLogic.Gesture gesture = editorHold.update(down, now);
                if (gesture == QuickMenuLogic.Gesture.TAP) ClientAccess.openEditor();
                else if (gesture == QuickMenuLogic.Gesture.HOLD && down) {
                    minecraft.setScreen(new QuickShapeScreen(minecraft.player));
                }
            }
        }
        QuickShapeAction.tick();
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

    /** Poll the physical binding because IN_GAME mappings become inactive while a Screen is open. */
    public static boolean isEditorKeyDown() {
        if (openEditor == null) return false;
        Minecraft mc = Minecraft.getInstance();
        InputConstants.Key key = openEditor.getKey();
        if (key.getType() == InputConstants.Type.MOUSE) {
            return GLFW.glfwGetMouseButton(mc.getWindow().getWindow(), key.getValue()) == GLFW.GLFW_PRESS;
        }
        return key.getType() == InputConstants.Type.KEYSYM && key.getValue() != InputConstants.UNKNOWN.getValue()
                && InputConstants.isKeyDown(mc.getWindow().getWindow(), key.getValue());
    }

    public static void cancelEditorGesture() {
        editorHold.cancel();
        suppressEditor = true;
    }

    /** Keep wheel instructions correct when the editor key is rebound. */
    public static Component editorKeyLabel() {
        return openEditor == null ? Component.literal("V") : openEditor.getTranslatedKeyMessage();
    }

    private static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        sentBypass = false;
        editorHold.cancel();
        suppressEditor = false;
        ClientState.reset();
        WorldPreview.clear();
    }
}
