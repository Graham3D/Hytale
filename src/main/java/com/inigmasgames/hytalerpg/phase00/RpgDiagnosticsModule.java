package com.inigmasgames.hytalerpg.phase00;

import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.event.events.player.PlayerMouseButtonEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerMouseMotionEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import java.nio.file.Path;
import java.util.List;

/** Public lifecycle seam for the retained package-private RPG diagnostics. */
public final class RpgDiagnosticsModule {
    private RpgDiagnosticsModule() {
    }

    public static void initialize(Path dataDirectory) {
        MouseProbeService.initialize(dataDirectory);
    }

    public static void observeRaw(PlayerRef playerRef, Object packet) {
        MouseProbeService.observeRaw(playerRef, packet);
    }

    public static void onButton(PlayerMouseButtonEvent event) {
        MouseProbeService.onButton(event);
    }

    public static void onMotion(PlayerMouseMotionEvent event) {
        MouseProbeService.onMotion(event);
    }

    public static List<AbstractCommand> commands() {
        return List.of(
                new CharacterProbeCommand(),
                new LinkCanvasProbeCommand(),
                new MouseProbeCommand(),
                new StatsProbeCommand(),
                new CapabilitiesProbeCommand(),
                new HtDevLibProbeCommand());
    }

    public static void close() {
        MouseProbeService.clear();
    }
}
