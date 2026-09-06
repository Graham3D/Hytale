package com.inigmasgames.canvasui;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerMouseButtonEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerMouseMotionEvent;
import com.hypixel.hytale.server.core.event.events.player.RemovedPlayerFromWorldEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.inigmasgames.canvasui.runtime.CanvasService;

import javax.annotation.Nonnull;

public final class CanvasUIPlugin extends JavaPlugin {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private CanvasService service;

    public CanvasUIPlugin(@Nonnull JavaPluginInit init) { super(init); }

    @Override
    protected void setup() {
        service = new CanvasService();
        CanvasUI.install(service);
        getEventRegistry().registerGlobal(PlayerMouseButtonEvent.class, service::route);
        getEventRegistry().registerGlobal(PlayerMouseMotionEvent.class, service::route);
        getEventRegistry().registerGlobal(PlayerDisconnectEvent.class,
                event -> service.close(event.getPlayerRef().getUuid(), "PLAYER_DISCONNECT"));
        getEventRegistry().registerGlobal(RemovedPlayerFromWorldEvent.class, event -> {
            PlayerRef playerRef = event.getHolder().getComponent(PlayerRef.getComponentType());
            if (playerRef != null) service.close(playerRef.getUuid(), "WORLD_TRANSITION");
        });
        LOGGER.atInfo().log("CANVASUI_SETUP revision=%s version=%s hytale=0.7.0-pre.1 fixedZoom=1.0 input=PlayerMouseEvents",
                CanvasUI.REVISION, getManifest().getVersion());
    }

    @Override
    protected void shutdown() {
        if (service != null) {
            service.close();
            CanvasUI.uninstall(service);
            service = null;
        }
        LOGGER.atInfo().log("CANVASUI_SHUTDOWN revision=%s", CanvasUI.REVISION);
    }
}
