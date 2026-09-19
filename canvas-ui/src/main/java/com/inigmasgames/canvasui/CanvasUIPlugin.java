package com.inigmasgames.canvasui;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerMouseButtonEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerMouseMotionEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerInteractEvent;
import com.hypixel.hytale.server.core.event.events.player.RemovedPlayerFromWorldEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.inigmasgames.canvasui.runtime.CanvasService;
import com.inigmasgames.canvasui.demo.CanvasDemoCommand;
import com.inigmasgames.canvasui.demo.CanvasCursorProbeCloseCommand;
import com.inigmasgames.canvasui.demo.CanvasCursorProbeCommand;
import com.inigmasgames.canvasui.demo.CanvasInputProbeCommand;
import com.inigmasgames.canvasui.runtime.cursor.CursorHudProbeService;
import com.inigmasgames.canvasui.runtime.cursor.CanvasInputGuard;

import javax.annotation.Nonnull;
import java.nio.file.Path;

public final class CanvasUIPlugin extends JavaPlugin {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private CanvasService service;
    private CursorHudProbeService cursorProbe;

    public CanvasUIPlugin(@Nonnull JavaPluginInit init) { super(init); }

    @Override
    protected void setup() {
        service = new CanvasService();
        cursorProbe = new CursorHudProbeService(getDataDirectory());
        getEntityStoreRegistry().registerSystem(new CanvasInputGuard.InteractionStartSystem(cursorProbe.inputGuard()));
        getEntityStoreRegistry().registerSystem(new CanvasInputGuard.ActiveSlotRequestSystem(cursorProbe.inputGuard()));
        getEntityStoreRegistry().registerSystem(new CanvasInputGuard.DropItemSystem(cursorProbe.inputGuard()));
        getEntityStoreRegistry().registerSystem(new CursorHudProbeService.DeathCleanupSystem(cursorProbe));
        CanvasUI.install(service, cursorProbe);
        getEventRegistry().registerGlobal(PlayerMouseButtonEvent.class, event -> {
            cursorProbe.route(event);
            PlayerRef playerRef = event.getPlayerRefComponent();
            if (playerRef == null || !cursorProbe.active(playerRef.getUuid())) service.route(event);
        });
        getEventRegistry().registerGlobal(PlayerMouseMotionEvent.class, event -> {
            cursorProbe.route(event);
            var entityRef = event.getPlayerRef();
            if (!entityRef.isValid()) return;
            PlayerRef playerRef = entityRef.getStore().getComponent(entityRef, PlayerRef.getComponentType());
            if (playerRef == null || !cursorProbe.active(playerRef.getUuid())) service.route(event);
        });
        getEventRegistry().registerGlobal(PlayerInteractEvent.class, cursorProbe::route);
        getEventRegistry().registerGlobal(PlayerDisconnectEvent.class, event -> {
            cursorProbe.close(event.getPlayerRef().getUuid(), "PLAYER_DISCONNECT");
            service.close(event.getPlayerRef().getUuid(), "PLAYER_DISCONNECT");
        });
        getEventRegistry().registerGlobal(RemovedPlayerFromWorldEvent.class, event -> {
            PlayerRef playerRef = event.getHolder().getComponent(PlayerRef.getComponentType());
            if (playerRef != null) {
                cursorProbe.close(playerRef.getUuid(), "WORLD_TRANSITION");
                service.close(playerRef.getUuid(), "WORLD_TRANSITION");
            }
        });
        Path demoLayouts = getDataDirectory().resolve("demo-layouts");
        getCommandRegistry().registerCommand(new CanvasDemoCommand(demoLayouts, false));
        getCommandRegistry().registerCommand(new CanvasDemoCommand(demoLayouts, true));
        getCommandRegistry().registerCommand(new CanvasInputProbeCommand());
        getCommandRegistry().registerCommand(new CanvasCursorProbeCommand("canvasui-cursor-probe",
                "Open the passive-HUD cursor-camera probe.", cursorProbe, CursorHudProbeService.Context.PASSIVE_HUD));
        getCommandRegistry().registerCommand(new CanvasCursorProbeCommand("canvasui-cursor-probe-nohud",
                "Open the cursor-camera probe without custom UI.", cursorProbe, CursorHudProbeService.Context.NO_HUD));
        getCommandRegistry().registerCommand(new CanvasCursorProbeCommand("canvasui-cursor-probe-page",
                "Open the cursor-camera probe with a CustomUI page control.", cursorProbe,
                CursorHudProbeService.Context.CUSTOM_PAGE_CONTROL));
        getCommandRegistry().registerCommand(new CanvasCursorProbeCommand("canvasui-cursor-drag-proof",
                "Open the calibrated passive-HUD two-node drag proof.", cursorProbe,
                CursorHudProbeService.Context.DRAG_PROOF));
        getCommandRegistry().registerCommand(new CanvasCursorProbeCloseCommand(cursorProbe));
        LOGGER.atInfo().log("CANVASUI_SETUP revision=%s version=%s hytale=%s inputBackend=%s capabilities=%s cursorBackend=%s cursorCapabilities=%s guardBoundary=INTERACTION_CHAIN_START cursorProbe=PHASE_A5_B_GATE",
                CanvasUI.REVISION, getManifest().getVersion(), CanvasUI.HYTALE_VERSION,
                service.inputBackend().id(), service.inputBackend().capabilities(),
                com.inigmasgames.canvasui.rendering.HytaleCursorHudInputBackend.INSTANCE.id(),
                com.inigmasgames.canvasui.rendering.HytaleCursorHudInputBackend.INSTANCE.capabilities());
        LOGGER.atInfo().log("CANVASUI_DEMO_SETUP revision=%s bundled=true commands=/canvasui-demo,/canvasui-topology-proof,/canvasui-input-probe,/canvasui-cursor-probe,/canvasui-cursor-probe-nohud,/canvasui-cursor-probe-page,/canvasui-cursor-drag-proof,/canvasui-cursor-probe-close",
                CanvasUI.REVISION);
    }

    @Override
    protected void shutdown() {
        CursorHudProbeService installedCursor = cursorProbe;
        if (cursorProbe != null) {
            cursorProbe.close();
            cursorProbe = null;
        }
        if (service != null) {
            service.close();
            CanvasUI.uninstall(service, installedCursor);
            service = null;
        }
        LOGGER.atInfo().log("CANVASUI_SHUTDOWN revision=%s", CanvasUI.REVISION);
    }
}
