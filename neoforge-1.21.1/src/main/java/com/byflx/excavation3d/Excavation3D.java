package com.byflx.excavation3d;

import com.byflx.excavation3d.config.ServerConfig;
import com.byflx.excavation3d.excavation.ExcavationService;
import com.byflx.excavation3d.net.ModNetwork;
import com.byflx.excavation3d.registry.ModComponents;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;

/** Common entry point (both sides). Client-only setup is in {@code client.Excavation3DClient}. */
@Mod(Excavation3D.MOD_ID)
public final class Excavation3D {
    public static final String MOD_ID = "excavation3d";

    public Excavation3D(IEventBus modBus, ModContainer container) {
        // No own item: shapes live on the tools in the excavation3d:shape_tools tag (plan §2a).
        ModComponents.COMPONENTS.register(modBus);
        container.registerConfig(ModConfig.Type.SERVER, ServerConfig.SPEC);
        modBus.addListener(ModNetwork::register);

        IEventBus gameBus = NeoForge.EVENT_BUS;
        gameBus.addListener(ModNetwork::onLogin);
        gameBus.addListener(ModNetwork::onLogout);
        gameBus.addListener(ModNetwork::onServerStopped);
        // Lowest priority: protection mods decide first; cancelled breaks are not delivered here.
        gameBus.addListener(EventPriority.LOWEST, ExcavationService::onBreak);
        // Drops are handled only after a successful removal; cancelled drops still prove it.
        gameBus.addListener(EventPriority.LOWEST, true, ExcavationService::onBlockDrops);
        gameBus.addListener(ExcavationService::onLeftClickBlock);
        gameBus.addListener(ExcavationService::onServerTick);
        gameBus.addListener(ExcavationService::onLogout);
        gameBus.addListener(ExcavationService::onChangedDimension);
        gameBus.addListener(ExcavationService::onDeath);
        gameBus.addListener(ExcavationService::onServerStopped);
    }
}
