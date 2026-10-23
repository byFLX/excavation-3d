package com.byflx.excavation3d.client;

import com.byflx.excavation3d.client.screen.ShapeEditorScreen;
import com.byflx.excavation3d.shape.ShapeLimits;
import com.byflx.excavation3d.shape.ShapeConfig;
import com.byflx.excavation3d.editor.QuickMenuLogic;
import com.byflx.excavation3d.data.ToolShapeData;
import com.byflx.excavation3d.text.ShapeTexts;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.network.chat.Component;

/** Client-only state: the server's limits, the open editor and the single-block key. Client thread only. */
public final class ClientState {
    private static ShapeLimits limits = ShapeLimits.DEFAULT;
    private static ShapeEditorScreen editor;
    private static boolean bypassActive;
    public record RememberedShape(ShapeConfig config, boolean worldPreview) { }
    private static final QuickMenuLogic.LastShape<RememberedShape> LAST = new QuickMenuLogic.LastShape<>();

    public static void expectRemoval(int sessionId, UUID player, ShapeConfig config, boolean worldPreview) {
        if (config != null) LAST.expectRemoval(sessionId, player, new RememberedShape(config, worldPreview));
    }

    public static void removalResult(int sessionId, boolean accepted) { LAST.onResult(sessionId, accepted); }

    public static Optional<RememberedShape> lastShape(UUID player) { return LAST.find(player); }

    /** Preset names remain client-only; the tool continues to store the existing shape format. */
    public static Component toolSummary(ToolShapeData data) {
        Component summary = ShapeTexts.toolSummary(data, limits);
        if (data != null && data.config().isPresent()) {
            ShapeConfig config = data.config().get().withRevision(0);
            for (String name : PresetRepository.get().names()) {
                if (PresetRepository.get().find(name).map(c -> c.withRevision(0).equals(config)).orElse(false)) {
                    return Component.translatable("tool.excavation3d.named", name, summary);
                }
            }
        }
        return summary;
    }

    private ClientState() {
    }

    public static ShapeLimits limits() {
        return limits;
    }

    /** Applies limits sent by the server; values outside the technical range fall back to the defaults. */
    public static void setLimits(int maxAxis, int maxSelected) {
        try {
            limits = new ShapeLimits(maxAxis, maxSelected);
        } catch (IllegalArgumentException e) {
            limits = ShapeLimits.DEFAULT;
        }
    }

    /** The editor that receives session and apply replies, also while a confirmation dialog covers it. */
    public static ShapeEditorScreen editor() {
        return editor;
    }

    public static void setEditor(ShapeEditorScreen screen) {
        editor = screen;
    }

    public static boolean bypassActive() {
        return bypassActive;
    }

    static void setBypassActive(boolean active) {
        bypassActive = active;
    }

    static void reset() {
        limits = ShapeLimits.DEFAULT;
        editor = null;
        bypassActive = false;
        QuickShapeAction.reset();
        ToolHud.reset();
        LAST.clearPending();
    }
}
