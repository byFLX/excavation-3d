package com.byflx.shapeexcavator;

import com.byflx.shapeexcavator.config.ServerConfig;
import com.byflx.shapeexcavator.excavation.ExcavationService;
import com.byflx.shapeexcavator.net.ModNetwork;
import com.byflx.shapeexcavator.registry.ModComponents;
import com.byflx.shapeexcavator.registry.ModItems;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;

/** Common entry point (both sides). Client-only setup is in {@code client.ShapeExcavatorClient}. */
@Mod(ShapeExcavator.MOD_ID)
public final class ShapeExcavator {
    public static final String MOD_ID = "shapeexcavator";

    public ShapeExcavator(IEventBus modBus, ModContainer container) {
        ModItems.ITEMS.register(modBus);
        ModComponents.COMPONENTS.register(modBus);
        container.registerConfig(ModConfig.Type.SERVER, ServerConfig.SPEC);
        modBus.addListener(ModNetwork::register);
        modBus.addListener(ModItems::addToCreativeTab);

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
