package com.inigmasgames.hytalerpg.commands;

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
import com.inigmasgames.hytalerpg.progress.AttributeAllocationService;
import com.inigmasgames.hytalerpg.ui.RpgUiProjectionService;
import com.inigmasgames.hytalerpg.ui.character.RpgCharacterPage;
import com.inigmasgames.hytalerpg.ui.trace.RpgUiTraceService;

public final class RpgCharacterCommand extends AbstractPlayerCommand {
    private final RpgUiProjectionService projection;
    private final AttributeAllocationService allocation;
    private final RpgUiTraceService trace;

    public RpgCharacterCommand(RpgUiProjectionService projection, AttributeAllocationService allocation,
                               RpgUiTraceService trace) {
        super("character", "Open the server-authoritative RPG Character screen.");
        this.projection = projection; this.allocation = allocation; this.trace = trace;
        setPermissionGroup(GameMode.Adventure);
    }

    @Override protected void execute(CommandContext context, Store<EntityStore> store, Ref<EntityStore> ref,
                                     PlayerRef playerRef, World world) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            context.sendMessage(Message.raw("Player UI manager is unavailable."));
            return;
        }
        player.getPageManager().openCustomPage(ref, store,
                new RpgCharacterPage(playerRef, projection, allocation, trace));
    }
}
