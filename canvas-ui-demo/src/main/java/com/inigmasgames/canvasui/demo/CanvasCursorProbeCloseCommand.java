package com.inigmasgames.canvasui.demo;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.inigmasgames.canvasui.runtime.cursor.CursorHudProbeService;

public final class CanvasCursorProbeCloseCommand extends AbstractPlayerCommand {
    private final CursorHudProbeService probe;

    public CanvasCursorProbeCloseCommand(CursorHudProbeService probe) {
        super("canvasui-cursor-probe-close", "Emergency-close the CanvasUI cursor-camera probe.");
        this.probe = probe;
        setPermissionGroup(GameMode.Adventure);
    }

    @Override protected void execute(CommandContext context, Store<EntityStore> store, Ref<EntityStore> ref,
                                     PlayerRef playerRef, World world) {
        boolean active = probe.active(playerRef.getUuid());
        probe.close(playerRef.getUuid(), "ADMINISTRATIVE_CLOSE_COMMAND");
        context.sendMessage(Message.raw(active ? "CanvasUI cursor probe closed; camera/HUD cleanup requested."
                : "No CanvasUI cursor probe was active."));
    }
}
