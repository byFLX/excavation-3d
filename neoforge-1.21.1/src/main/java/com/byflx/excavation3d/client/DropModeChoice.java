package com.byflx.excavation3d.client;

import com.byflx.excavation3d.config.ClientConfig;
import com.byflx.excavation3d.excavation.DropMode;
import com.byflx.excavation3d.excavation.DropPolicy;
import com.byflx.excavation3d.net.DropModePayload;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** Independent of the editor draft: saves and sends immediately, including external config edits. */
public final class DropModeChoice {
    private static DropMode sent;
    private DropModeChoice() { }
    public static Component name(DropMode mode) {
        return Component.translatable("drops.excavation3d." + mode.name().toLowerCase(Locale.ROOT));
    }
    public static DropMode effective() { return ClientState.dropPolicy().resolve(ClientConfig.dropMode()); }
    public static Component label() { return Component.translatable("gui.excavation3d.drops", name(effective())); }
    public static boolean forced() { return ClientState.dropPolicy() != DropPolicy.PLAYER_CHOICE; }
    public static Component tooltip() {
        return forced() ? Component.translatable("gui.excavation3d.drops.forced")
                : Component.translatable("gui.excavation3d.drops.tooltip");
    }
    public static void cycle() {
        if (forced()) return;
        ClientConfig.setDropMode(ClientConfig.dropMode().next());
        sync();
        ToolHud.feedback(label());
    }
    public static void sync() {
        var connection = Minecraft.getInstance().getConnection();
        DropMode mode = ClientConfig.dropMode();
        if (connection != null && connection.hasChannel(DropModePayload.TYPE) && sent != mode) {
            PacketDistributor.sendToServer(new DropModePayload(mode));
            sent = mode;
        }
    }
    public static void reset() { sent = null; }
}
