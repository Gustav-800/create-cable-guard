package com.cableguard;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

@Mod("cableguard")
public class CableGuard {

    public CableGuard(IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener(SelfTest::onServerStarted);
        NeoForge.EVENT_BUS.addListener(SelfTest::onServerTick);
    }
}
