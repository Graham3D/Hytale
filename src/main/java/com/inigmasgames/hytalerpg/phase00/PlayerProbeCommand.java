package com.inigmasgames.hytalerpg.phase00;

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

abstract class PlayerProbeCommand extends AbstractPlayerCommand {
    PlayerProbeCommand(String name, String description) {
        super(name, description);
        setPermissionGroup(GameMode.Adventure);
    }

    @Override
    protected final void execute(CommandContext context, Store<EntityStore> store,
                                 Ref<EntityStore> ref, PlayerRef playerRef, World world) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            context.sendMessage(Message.raw("Phase 00 probe failed: Player component unavailable."));
            return;
        }
        executeProbe(context, store, ref, playerRef, player);
    }

    protected abstract void executeProbe(CommandContext context, Store<EntityStore> store,
                                         Ref<EntityStore> ref, PlayerRef playerRef, Player player);
}

