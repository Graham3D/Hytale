package com.inigmasgames.hytalerpg.phase00;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;

import javax.annotation.Nonnull;

/** Audit-only plugin. It intentionally contains no RPG gameplay systems. */
public final class Phase00Plugin extends JavaPlugin {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    public Phase00Plugin(@Nonnull JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        LOGGER.atInfo().log("PHASE00 setup: temporary evidence probes only; no gameplay mutations");
        getCommandRegistry().registerCommand(new CharacterProbeCommand());
        getCommandRegistry().registerCommand(new LinkCanvasProbeCommand());
        getCommandRegistry().registerCommand(new HudShowProbeCommand());
        getCommandRegistry().registerCommand(new HudClearProbeCommand());
        getCommandRegistry().registerCommand(new StatsProbeCommand());
        getCommandRegistry().registerCommand(new CapabilitiesProbeCommand());
    }
}

