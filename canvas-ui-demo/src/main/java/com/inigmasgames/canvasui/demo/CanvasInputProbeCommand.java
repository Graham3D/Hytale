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
import com.inigmasgames.canvasui.rendering.CanvasInputProbePage;

public final class CanvasInputProbeCommand extends AbstractPlayerCommand {
    public CanvasInputProbeCommand() {
        super("canvasui-input-probe", "Open the CanvasUI CustomUI input capability probe.");
        setPermissionGroup(GameMode.Adventure);
    }
    @Override protected void execute(CommandContext context, Store<EntityStore> store, Ref<EntityStore> ref,
            PlayerRef playerRef, World world) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) { context.sendMessage(Message.raw("CanvasUI probe failed: Player unavailable.")); return; }
        player.getPageManager().openCustomPage(ref, store, new CanvasInputProbePage(playerRef));
        context.sendMessage(Message.raw("CanvasUI input probe opened. Interact with each labeled control, then close it."));
    }
}
