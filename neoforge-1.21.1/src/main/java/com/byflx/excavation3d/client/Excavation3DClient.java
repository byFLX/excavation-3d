package com.byflx.excavation3d.client;

import com.byflx.excavation3d.Excavation3D;
import com.byflx.excavation3d.data.ToolShapeData;
import com.byflx.excavation3d.net.BypassPayload;
import com.byflx.excavation3d.editor.QuickMenuLogic;
import com.byflx.excavation3d.editor.PresetShortcuts;
import com.byflx.excavation3d.shape.ShapeConfig;
import com.byflx.excavation3d.client.screen.QuickShapeScreen;
import com.byflx.excavation3d.registry.ModTags;
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
@Mod(value = Excavation3D.MOD_ID, dist = Dist.CLIENT)
public final class Excavation3DClient {
    private static final String CATEGORY = "key.categories.excavation3d";
    private static KeyMapping openEditor;
    private static KeyMapping singleBlock;
    private static KeyMapping toggleShape, nextPreset, previousPreset, toggleWorldPreview;
    private static final KeyMapping[] presetSlots = new KeyMapping[5];
    private static boolean sentBypass;
    private static final QuickMenuLogic.Hold editorHold = new QuickMenuLogic.Hold();
    private static boolean suppressEditor;

    public Excavation3DClient(IEventBus modBus) {
        modBus.addListener(Excavation3DClient::registerKeys);
        NeoForge.EVENT_BUS.addListener(Excavation3DClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(Excavation3DClient::onInteractionKey);
        NeoForge.EVENT_BUS.addListener(Excavation3DClient::onLoggingOut);
        NeoForge.EVENT_BUS.addListener(WorldPreview::onRenderLevel);
        NeoForge.EVENT_BUS.addListener(ToolHud::onRenderGui);
        NeoForge.EVENT_BUS.addListener(Excavation3DClient::onTooltip);
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
        tooltip.add(at + 1, Component.translatable("tool.excavation3d.hint", openEditor.getTranslatedKeyMessage(),
                singleBlock.getTranslatedKeyMessage()).withStyle(ChatFormatting.DARK_GRAY));
    }

    private static void registerKeys(RegisterKeyMappingsEvent event) {
        // Created here rather than statically, so nothing touches input state before the client exists.
        openEditor = new KeyMapping("key.excavation3d.open_editor", KeyConflictContext.IN_GAME,
                InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, CATEGORY);
        singleBlock = new KeyMapping("key.excavation3d.single_block", KeyConflictContext.IN_GAME,
                InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_SHIFT, CATEGORY);
        event.register(openEditor);
        event.register(singleBlock);
        toggleShape = registerUnbound(event, "toggle_shape");
        nextPreset = registerUnbound(event, "next_preset");
        previousPreset = registerUnbound(event, "previous_preset");
        toggleWorldPreview = registerUnbound(event, "toggle_world_preview");
        for (int i = 0; i < presetSlots.length; i++) {
            presetSlots[i] = registerUnbound(event, "apply_preset_" + (i + 1));
        }
    }

    private static KeyMapping registerUnbound(RegisterKeyMappingsEvent event, String name) {
        KeyMapping mapping = new KeyMapping("key.excavation3d." + name, KeyConflictContext.IN_GAME,
                InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), CATEGORY);
        event.register(mapping);
        return mapping;
    }

    /** Drain every mapping even while blocked: clicks must never replay after a screen or request closes. */
    private static void shortcuts(Minecraft mc) {
        KeyMapping[] mappings = {toggleShape, nextPreset, previousPreset,
                presetSlots[0], presetSlots[1], presetSlots[2], presetSlots[3], presetSlots[4], toggleWorldPreview};
        int selected = -1;
        for (int i = 0; i < mappings.length; i++) {
            if (mappings[i] == null) continue;
            while (mappings[i].consumeClick()) if (selected < 0) selected = i;
        }
        if (selected < 0 || mc.screen != null || mc.player == null || QuickShapeAction.busy()
                || !mc.player.getMainHandItem().is(ModTags.SHAPE_TOOLS)) return;
        if (selected == mappings.length - 1) {
            QuickShapeAction.toggleWorldPreview();
            return;
        }
        ToolShapeData data = ModTags.shapeOf(mc.player.getMainHandItem());
        ShapeConfig active = data == null ? null : data.config().orElse(null);
        if (selected == 0) {
            var last = ClientState.lastShape(mc.player.getUUID());
            switch (PresetShortcuts.toggle(active != null, last.isPresent())) {
                case OFF -> QuickShapeAction.start(null, false);
                case RESTORE -> QuickShapeAction.start(last.orElseThrow().config(), last.orElseThrow().worldPreview());
                case NONE -> { }
            }
            return;
        }
        PresetRepository presets = PresetRepository.get();
        var name = selected <= 2
                ? PresetShortcuts.cycle(presets.names(), presets::find, active, selected == 1 ? 1 : -1,
                        QuickShapeAction.confirmedPresetName())
                : PresetShortcuts.nth(presets.names(), selected - 2);
        name.ifPresent(n -> presets.find(n).ifPresent(config -> QuickShapeAction.startPreset(config,
                data != null && data.worldPreview(), n)));
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
        shortcuts(minecraft);
        QuickShapeAction.tick();
        syncBypass(minecraft);
        WorldPreview.tick(minecraft);
        ToolHud.tick(minecraft);
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
