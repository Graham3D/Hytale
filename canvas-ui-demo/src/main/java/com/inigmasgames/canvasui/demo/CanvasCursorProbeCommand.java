package com.inigmasgames.canvasui.demo;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.inigmasgames.canvasui.runtime.cursor.CursorHudProbeService;

public final class CanvasCursorProbeCommand extends AbstractPlayerCommand {
    private final CursorHudProbeService probe;
    private final CursorHudProbeService.Context probeContext;

    public CanvasCursorProbeCommand(String name, String description, CursorHudProbeService probe,
                                    CursorHudProbeService.Context probeContext) {
        super(name, description);
        this.probe = probe;
        this.probeContext = probeContext;
        setPermissionGroup(GameMode.Adventure);
    }

    @Override protected void execute(CommandContext context, Store<EntityStore> store, Ref<EntityStore> ref,
                                     PlayerRef playerRef, World world) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            context.sendMessage(Message.raw("CanvasUI cursor probe failed: Player unavailable."));
            return;
        }
        CursorHudProbeService.OpenResult result = probe.open(probeContext, player, playerRef, world, store, ref);
        context.sendMessage(Message.raw(result.message()));
        if (result.opened()) context.sendMessage(Message.raw(
                probeContext == CursorHudProbeService.Context.DRAG_PROOF
                        ? "Drag either real graph node and release. Then test attack/place/use/abilities/hotbar/drop are blocked. Use CLOSE or /canvasui-cursor-probe-close."
                        : "Test movement plus left/middle/right press-drag-release and guarded gameplay inputs. Use CLOSE or /canvasui-cursor-probe-close."));
    }
}
