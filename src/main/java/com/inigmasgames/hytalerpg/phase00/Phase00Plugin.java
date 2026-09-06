package com.inigmasgames.hytalerpg.phase00;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.event.events.player.PlayerMouseButtonEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerMouseMotionEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.io.adapter.PacketAdapters;
import com.hypixel.hytale.server.core.io.adapter.PacketFilter;
import com.hypixel.hytale.server.core.io.adapter.PlayerPacketWatcher;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;

import javax.annotation.Nonnull;

/** Audit-only plugin. It intentionally contains no RPG gameplay systems. */
public final class Phase00Plugin extends JavaPlugin {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private PacketFilter inboundWatcher;

    public Phase00Plugin(@Nonnull JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        LOGGER.atInfo().log("PHASE00_SETUP revision=%s version=%s hytale=%s stage=%s observationOnly=true",
                BuildIdentity.REVISION, BuildIdentity.VERSION, BuildIdentity.HYTALE_VERSION,
                BuildIdentity.STAGE);
        MouseProbeService.initialize(getDataDirectory());
        inboundWatcher = PacketAdapters.registerInbound((PlayerPacketWatcher) (playerRef, packet) -> {
            AbilityInputObserver.observe(playerRef, packet);
            MouseProbeService.observeRaw(playerRef, packet);
        });
        getEventRegistry().registerGlobal(PlayerMouseButtonEvent.class, MouseProbeService::onButton);
        getEventRegistry().registerGlobal(PlayerMouseMotionEvent.class, MouseProbeService::onMotion);
        getEventRegistry().registerGlobal(PlayerReadyEvent.class, event -> {
            var ref = event.getPlayerRef();
            var playerRef = ref.getStore().getComponent(ref,
                    com.hypixel.hytale.server.core.universe.PlayerRef.getComponentType());
            if (playerRef != null && event.getPlayer().getHudManager().getCustomHud(RevisionHud.KEY) == null) {
                event.getPlayer().getHudManager().addCustomHud(playerRef, new RevisionHud(playerRef));
                LOGGER.atInfo().log("PHASE00_REVISION_HUD revision=%s player=%s readyId=%d",
                        BuildIdentity.REVISION, playerRef.getUuid(), event.getReadyId());
            }
        });
        getCommandRegistry().registerCommand(new CharacterProbeCommand());
        getCommandRegistry().registerCommand(new LinkCanvasProbeCommand());
        getCommandRegistry().registerCommand(new MouseProbeCommand());
        getCommandRegistry().registerCommand(new HudShowProbeCommand());
        getCommandRegistry().registerCommand(new HudClearProbeCommand());
        getCommandRegistry().registerCommand(new StatsProbeCommand());
        getCommandRegistry().registerCommand(new CapabilitiesProbeCommand());
        getCommandRegistry().registerCommand(new AbilityInputsProbeCommand());
        getCommandRegistry().registerCommand(new HtDevLibProbeCommand());
    }

    @Override
    protected void shutdown() {
        if (inboundWatcher != null) {
            PacketAdapters.deregisterInbound(inboundWatcher);
            inboundWatcher = null;
        }
        MouseProbeService.clear();
        LOGGER.atInfo().log("PHASE00_SHUTDOWN revision=%s", BuildIdentity.REVISION);
    }
}
